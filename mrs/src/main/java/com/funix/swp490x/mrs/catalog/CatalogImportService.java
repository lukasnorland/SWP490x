package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.CatalogImportRun;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.CatalogImportRunRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Brings MySQL in step with the staged catalog (UC-28).
 *
 * <p>Work is decided from the object listing alone. Every listing entry carries
 * an ETag, and {@code song.source_etag} records the hash each row was built
 * from, so an object is downloaded only when it is new or its content changed.
 * A sync over an untouched prefix therefore costs one listing and no reads,
 * which is what makes it safe to run on a schedule.
 *
 * <p>Startup and the scheduled poller call {@link #sync} and wait. The P-06c
 * button calls {@link #startAsync} so the page can poll {@link #progress}
 * instead of sitting on a frozen POST. Only one may run at a time; a second
 * caller is told the catalog is already syncing rather than racing the first.
 */
@Service
public class CatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportService.class);

    /**
     * Rows per transaction. Small enough that a failure loses little work, big
     * enough that 3,000 songs do not mean 3,000 commits. The next run picks up
     * whatever did not commit, because those objects still differ by ETag.
     */
    private static final int CHUNK_SIZE = 200;

    /** Concurrent object reads. Enough to hide latency, few enough to stay polite. */
    private static final int FETCH_THREADS = 8;

    private final CatalogObjectStore store;
    private final SongRepository songRepository;
    private final CatalogImportRunRepository runRepository;
    private final AuditLogRepository auditLogRepository;
    private final SongUpserter upserter;
    private final CoverAmbienceService coverAmbience;

    /**
     * Single instance, single process, so a flag is enough. Scaling to more
     * than one instance would need a shared lock instead.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicReference<ImportProgress> snapshot =
            new AtomicReference<>(ImportProgress.idle());

    /** One background import at a time; the HTTP request must not hold it. */
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "catalog-import");
        thread.setDaemon(true);
        return thread;
    });

    public CatalogImportService(CatalogObjectStore store,
            SongRepository songRepository,
            CatalogImportRunRepository runRepository,
            AuditLogRepository auditLogRepository,
            SongUpserter upserter,
            CoverAmbienceService coverAmbience) {
        this.store = store;
        this.songRepository = songRepository;
        this.runRepository = runRepository;
        this.auditLogRepository = auditLogRepository;
        this.upserter = upserter;
        this.coverAmbience = coverAmbience;
    }

    /**
     * @param force read and re-apply every object, ignoring stored ETags. For
     *     recovering from a bad import; a normal run never needs it.
     */
    public ImportSummary sync(ImportTrigger trigger, Long actorId, boolean force) {
        if (!running.compareAndSet(false, true)) {
            log.info("Catalog sync already in progress; {} trigger ignored", trigger);
            return ImportSummary.refused();
        }
        try {
            return runLocked(trigger, actorId, force);
        } finally {
            running.set(false);
        }
    }

    /**
     * Starts a sync on a background thread so P-06c can return immediately and
     * poll {@link #progress()}.
     *
     * @return false when another import already holds the lock
     */
    public boolean startAsync(ImportTrigger trigger, Long actorId, boolean force) {
        if (!running.compareAndSet(false, true)) {
            log.info("Catalog sync already in progress; {} trigger ignored", trigger);
            return false;
        }
        publish(true, "starting", "Starting import…", 0, 0, 0, 0, 0, 0, 1);
        try {
            importExecutor.execute(() -> {
                try {
                    runLocked(trigger, actorId, force);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return true;
    }

    public ImportProgress progress() {
        return snapshot.get();
    }

    @PreDestroy
    void shutdown() {
        importExecutor.shutdownNow();
    }

    private ImportSummary runLocked(ImportTrigger trigger, Long actorId, boolean force) {
        publish(true, "listing", "Listing staged songs…", 0, 0, 0, 0, 0, 0, 3);
        CatalogImportRun run = runRepository.save(new CatalogImportRun(trigger, actorId));
        try {
            ImportSummary summary = doSync(force);
            record(run, summary);
            publishFinished(summary);
            return summary;
        } catch (RuntimeException e) {
            log.error("Catalog sync ({}) failed", trigger, e);
            ImportSummary failed = new ImportSummary(0, 0, 0, 0, List.of(), false, message(e));
            try {
                record(run, failed);
            } catch (RuntimeException recordFailure) {
                // The original cause is what matters; losing the record of it is
                // not worth replacing the reported error with a second one.
                log.error("Could not record the failed catalog sync", recordFailure);
            }
            publish(false, "failed", "The import could not be completed.",
                    0, 0, 0, 0, 0, 0, 100);
            return failed;
        }
    }

    /**
     * BR-10 wants an actor against every state change. Only a run someone
     * triggered has one, so the audit entry is written here rather than in the
     * controller, and an unattended run relies on {@code catalog_import_run}.
     */
    private void audit(CatalogImportRun run, ImportSummary summary) {
        if (run.getActorId() == null) {
            return;
        }
        String details = "{\"listed\":%d,\"read\":%d,\"added\":%d,\"updated\":%d,\"skipped\":%d}"
                .formatted(summary.listed(), summary.read(), summary.added(), summary.updated(),
                        summary.skipped());
        try {
            auditLogRepository.save(new AuditLog(run.getActorId(),
                    AuditLog.ACTION_CATALOG_IMPORT,
                    AuditLog.ENTITY_CATALOG_IMPORT_RUN,
                    run.getId(),
                    details));
        } catch (RuntimeException e) {
            // Songs are already committed, so failing the call now would report a
            // successful import as broken. catalog_import_run still holds the run.
            log.error("Catalog import {} was applied but could not be written to the audit log",
                    run.getId(), e);
        }
    }

    /** What a sync would do right now, without changing anything (P-06c). */
    public PendingChanges pendingChanges() {
        List<CatalogObject> listed = store.list();
        Map<String, String> known = knownEtags();
        int newObjects = 0;
        int changed = 0;
        for (CatalogObject object : listed) {
            switch (classify(object, known)) {
                case NEW -> newObjects++;
                case CHANGED -> changed++;
                case UNCHANGED -> {
                    // Nothing to report.
                }
            }
        }
        return new PendingChanges(listed.size(), newObjects, changed, store.describe());
    }

    public Optional<CatalogImportRun> lastRun() {
        return runRepository.findFirstByOrderByStartedAtDesc();
    }

    public boolean isRunning() {
        return running.get();
    }

    private ImportSummary doSync(boolean force) {
        List<CatalogObject> listed = store.list();
        Map<String, String> known = force ? Map.of() : knownEtags();

        List<CatalogObject> toRead = listed.stream()
                .filter(o -> force || classify(o, known) != Classification.UNCHANGED)
                .toList();

        if (toRead.isEmpty()) {
            log.info("Catalog sync: {} object(s) listed, all in step", listed.size());
        } else {
            log.info("Catalog sync: {} object(s) listed, {} to read", listed.size(), toRead.size());
        }

        List<SkippedRow> skipped = Collections.synchronizedList(new ArrayList<>());
        int added = 0;
        int updated = 0;
        publishImporting(listed.size(), toRead.size(), 0, 0, 0, 0);

        try (ExecutorService pool = Executors.newFixedThreadPool(FETCH_THREADS)) {
            for (int from = 0; from < toRead.size(); from += CHUNK_SIZE) {
                List<CatalogObject> chunk = toRead.subList(from,
                        Math.min(from + CHUNK_SIZE, toRead.size()));

                List<Fetched> fetched = fetch(pool, chunk, skipped);
                try {
                    SongUpserter.ChunkResult result = upserter.upsertChunk(fetched);
                    added += result.added();
                    updated += result.updated();
                    skipped.addAll(result.skipped());
                } catch (RuntimeException e) {
                    // One bad chunk must not lose the ones already committed.
                    // Those objects stay unchanged by ETag, so the next run
                    // retries exactly them.
                    log.error("Catalog sync: chunk of {} failed and was left for the next run",
                            chunk.size(), e);
                    for (Fetched item : fetched) {
                        skipped.add(new SkippedRow(item.key(), "chunk failed: " + message(e)));
                    }
                }
                publishImporting(listed.size(), toRead.size(),
                        from + chunk.size(), added, updated, skipped.size());
            }

            // After the rows exist, so songs this run added are covered, and a
            // catalog imported before the wash existed fills in over a few runs
            // even when every object is already in step.
            publish(true, "covers", "Sampling cover colours…",
                    listed.size(), toRead.size(), toRead.size(),
                    added, updated, skipped.size(), 92);
            sampleCovers(pool);
        }

        return new ImportSummary(listed.size(), toRead.size(), added, updated,
                List.copyOf(skipped), false, null);
    }

    /**
     * The catalog is already in step by this point, so a cover host being down
     * is not a failed import; those songs keep no wash and are offered again by
     * the next run.
     */
    private void sampleCovers(ExecutorService pool) {
        try {
            coverAmbience.fillMissing(pool);
        } catch (RuntimeException e) {
            log.warn("Catalog sync: the cover ambience pass did not finish", e);
        }
    }

    /**
     * Reads a chunk with a bounded width. The first bulk load is thousands of
     * small HTTPS round trips to Singapore, which dominates the run when done
     * one at a time; later syncs read only what changed. The width is capped
     * rather than left to the common pool so a big load cannot crowd out the
     * rest of the application or trip S3 request limits.
     */
    private List<Fetched> fetch(ExecutorService pool, List<CatalogObject> chunk,
            List<SkippedRow> skipped) {

        List<CompletableFuture<Fetched>> futures = chunk.stream()
                .map(object -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return new Fetched(object, store.readJson(object.key()));
                    } catch (CatalogStoreException e) {
                        log.warn("Catalog sync: could not read {}", object.key(), e);
                        skipped.add(new SkippedRow(object.key(), "could not be read"));
                        return null;
                    }
                }, pool))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, String> knownEtags() {
        Map<String, String> known = new HashMap<>();
        for (Object[] pair : songRepository.findExternalIdAndEtagPairs()) {
            known.put((String) pair[0], (String) pair[1]);
        }
        return known;
    }

    /**
     * A key not following the {@code <externalSourceId>.json} convention cannot
     * be matched to a row without opening it, so it is always read.
     */
    private Classification classify(CatalogObject object, Map<String, String> known) {
        String id = object.externalSourceIdHint();
        if (id == null) {
            return Classification.CHANGED;
        }
        if (!known.containsKey(id)) {
            return Classification.NEW;
        }
        String storedEtag = known.get(id);
        if (storedEtag == null || object.etag() == null || !storedEtag.equals(object.etag())) {
            return Classification.CHANGED;
        }
        return Classification.UNCHANGED;
    }

    /**
     * Closes the run in its own transaction, so a failed sync is still on
     * record even though its own work rolled back.
     */
    private void record(CatalogImportRun run, ImportSummary summary) {
        run.setFinishedAt(LocalDateTime.now());
        run.setObjectsListed(summary.listed());
        run.setAdded(summary.added());
        run.setUpdated(summary.updated());
        run.setSkipped(summary.skipped());
        run.setError(summary.error());
        runRepository.save(run);
        audit(run, summary);
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private void publishImporting(int listed, int toRead, int processed,
            int added, int updated, int skipped) {
        String detail = toRead == 0
                ? "Catalog is already in step. Checking cover colours…"
                : "Importing songs…";
        int percent = toRead == 0 ? 50 : Math.min(90, Math.round(90f * processed / toRead));
        publish(true, "importing", detail, listed, toRead, processed, added, updated, skipped,
                percent);
    }

    private void publishFinished(ImportSummary summary) {
        String detail = summary.isNoChange()
                ? "Nothing to import — every staged song is already in the catalog."
                : "Import finished.";
        publish(false, "done", detail, summary.listed(), summary.read(), summary.read(),
                summary.added(), summary.updated(), summary.skipped(), 100);
    }

    private void publish(boolean running, String phase, String detail, int listed, int toRead,
            int processed, int added, int updated, int skipped, int percent) {
        snapshot.set(new ImportProgress(running, phase, detail, listed, toRead, processed,
                added, updated, skipped, percent));
    }

    private enum Classification {
        NEW, CHANGED, UNCHANGED
    }

    /** One object and its body, ready to be mapped. */
    record Fetched(CatalogObject object, String json) {

        String key() {
            return object.key();
        }
    }

    /**
     * Live status for the P-06c progress panel. {@code running} is the signal
     * to keep polling; the counts are whatever the current (or last) run has
     * applied so far.
     */
    public record ImportProgress(
            boolean running,
            String phase,
            String detail,
            int listed,
            int toRead,
            int processed,
            int added,
            int updated,
            int skipped,
            int percent) {

        public static ImportProgress idle() {
            return new ImportProgress(false, "idle", "", 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /** What a sync would do, for the P-06c panel. */
    public record PendingChanges(int listed, int newObjects, int changed, String source) {

        public int total() {
            return newObjects + changed;
        }

        public int unchanged() {
            return listed - total();
        }
    }
}
