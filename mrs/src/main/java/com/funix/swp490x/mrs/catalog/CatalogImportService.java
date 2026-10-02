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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Syncs staged JSON into MySQL using ETags and optimistic locking (UC-28, BR-06).
 * Runs one import per process; manual imports expose asynchronous progress.
 */
@Service
public class CatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportService.class);

    /** Commits each chunk independently; uncommitted objects are retried on the next sync. */
    private static final int CHUNK_SIZE = 200;

    /** Maximum concurrent object reads. */
    private static final int FETCH_THREADS = 8;

    private final CatalogObjectStore store;
    private final SongRepository songRepository;
    private final CatalogImportRunRepository runRepository;
    private final AuditLogRepository auditLogRepository;
    private final SongUpserter upserter;
    private final CoverAmbienceService coverAmbience;

    /** Process-local import guard; multiple instances would require a shared lock. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicReference<ImportProgress> snapshot =
            new AtomicReference<>(ImportProgress.idle());

    /** Background executor for the single asynchronous import. */
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

    /** @param force reapply all objects, including those with unchanged ETags */
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
     * Starts an asynchronous sync for P-06b progress polling.
     * @return false when another import is running
     */
    public boolean startAsync(ImportTrigger trigger, Long actorId, boolean force) {
        return startAsync(trigger, actorId, force, List.of());
    }

    /** Carry upload validation failures into the same import audit and summary. */
    public boolean startAsync(ImportTrigger trigger, Long actorId, boolean force,
            List<SkippedRow> uploadRejections) {
        List<SkippedRow> rejected = List.copyOf(uploadRejections);
        if (!running.compareAndSet(false, true)) {
            log.info("Catalog sync already in progress; {} trigger ignored", trigger);
            return false;
        }
        publish(true, "starting", "Starting import…", 0, 0, 0, 0, 0, 0, 1);
        try {
            importExecutor.execute(() -> {
                try {
                    runLocked(trigger, actorId, force, rejected);
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
        return runLocked(trigger, actorId, force, List.of());
    }

    private ImportSummary runLocked(ImportTrigger trigger, Long actorId, boolean force,
            List<SkippedRow> uploadRejections) {
        publish(true, "listing", "Listing staged songs…", 0, 0, 0, 0, 0, 0, 3);
        CatalogImportRun run = runRepository.save(new CatalogImportRun(trigger, actorId));
        try {
            ImportSummary summary = doSync(force, actorId);
            if (!uploadRejections.isEmpty()) {
                List<SkippedRow> rejected = new ArrayList<>(uploadRejections);
                rejected.addAll(summary.skippedRows());
                summary = new ImportSummary(summary.listed(), summary.read(), summary.added(),
                        summary.updated(), summary.removed(), List.copyOf(rejected),
                        summary.alreadyRunning(), summary.error());
            }
            record(run, summary);
            publishFinished(summary);
            return summary;
        } catch (RuntimeException e) {
            log.error("Catalog sync ({}) failed", trigger, e);
            ImportSummary failed = new ImportSummary(0, 0, 0, 0, 0, uploadRejections, false, message(e));
            try {
                record(run, failed);
            } catch (RuntimeException recordFailure) {
                // Preserve the import failure if recording its outcome also fails.
                log.error("Could not record the failed catalog sync", recordFailure);
            }
            publish(false, "failed", "The import could not be completed.",
                    0, 0, 0, 0, 0, 0, 100);
            return failed;
        }
    }

    /**
     * Records actor-triggered imports in {@code audit_log}; unattended runs use {@code catalog_import_run}.
     */
    private void audit(CatalogImportRun run, ImportSummary summary) {
        if (run.getActorId() == null) {
            return;
        }
        try {
            String details = JsonMapper.builder().build().writeValueAsString(Map.of(
                    "listed", summary.listed(), "read", summary.read(),
                    "added", summary.added(), "updated", summary.updated(),
                    "skipped", summary.skipped(), "skippedRows", summary.skippedRows().stream()
                            .map(CatalogImportService::auditRejection).toList()));
            auditLogRepository.save(new AuditLog(run.getActorId(),
                    AuditLog.ACTION_CATALOG_IMPORT,
                    AuditLog.ENTITY_CATALOG_IMPORT_RUN,
                    run.getId(),
                    details));
        } catch (RuntimeException e) {
            // The import already committed; an audit failure must not change its reported outcome.
            log.error("Catalog import {} was applied but could not be written to the audit log",
                    run.getId(), e);
        }
    }

    /** Returns the configured staging location without accessing the object store. */
    public String sourceDescription() {
        return store.describe();
    }

    private static Map<String, String> auditRejection(SkippedRow row) {
        if (row.reason().startsWith("missing ")) {
            return Map.of("key", row.key(), "reason", row.reason(), "code", "MSG_019",
                    "message", "This Sync Catalog row was missing a required field and was skipped.");
        }
        if (row.reason().startsWith("unregistered provider")) {
            return Map.of("key", row.key(), "reason", row.reason(), "code", "MSG_020",
                    "message", "Unregistered catalog provider. Please register the provider before importing.");
        }
        return Map.of("key", row.key(), "reason", row.reason());
    }

    /** Previews the current sync changes without writing them; requires an object listing. */
    public PendingChanges pendingChanges() {
        List<CatalogObject> listed;
        try {
            listed = store.list();
        } catch (RuntimeException e) {
            throw CatalogStoreException.of("Could not list " + store.describe(), e);
        }
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

    private ImportSummary doSync(boolean force, Long actorId) {
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

                List<Fetched> fetched = fetch(pool, chunk, skipped, known);
                try {
                    SongUpserter.ChunkResult result = upserter.upsertChunk(fetched);
                    added += result.added();
                    updated += result.updated();
                    skipped.addAll(result.skipped());
                } catch (RuntimeException e) {
                    // Preserve committed chunks; the next sync retries objects whose ETags still differ.
                    log.error("Catalog sync: chunk of {} failed and was left for the next run",
                            chunk.size(), e);
                    for (Fetched item : fetched) {
                        skipped.add(new SkippedRow(item.key(), "chunk failed: " + message(e)));
                    }
                }
                publishImporting(listed.size(), toRead.size(),
                        from + chunk.size(), added, updated, skipped.size());
            }

            // Sample covers after upsert, including unsampled covers on unchanged songs.
            publish(true, "covers", "Sampling cover colours…",
                    listed.size(), toRead.size(), toRead.size(),
                    added, updated, skipped.size(), 92);
            sampleCovers(pool);
        }

        int removed = pruneMissing(listed, actorId);
        if (removed > 0) {
            log.info("Catalog sync: removed {} song(s) no longer staged", removed);
        }

        int unusedTags = upserter.pruneUnusedTags();
        if (unusedTags > 0) {
            log.info("Catalog sync: dropped {} unused tag(s)", unusedTags);
        }

        return new ImportSummary(listed.size(), toRead.size(), added, updated, removed,
                List.copyOf(skipped), false, null);
    }

    /** Prunes missing staged songs; an empty listing never clears the catalog. */
    private int pruneMissing(List<CatalogObject> listed, Long actorId) {
        if (listed.isEmpty()) {
            log.warn("Catalog sync: listing was empty; not removing songs");
            return 0;
        }
        Set<String> staged = listed.stream()
                .map(CatalogObject::externalSourceIdHint)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (staged.isEmpty()) {
            log.warn("Catalog sync: listing had no staging filenames; not removing songs");
            return 0;
        }
        List<String> gone = new ArrayList<>();
        for (Object[] pair : songRepository.findIdAndExternalIdPairs()) {
            String externalId = (String) pair[1];
            if (externalId != null && !staged.contains(externalId)) {
                gone.add(externalId);
            }
        }
        if (gone.isEmpty()) {
            return 0;
        }
        try {
            return upserter.removeMissing(gone, actorId);
        } catch (OptimisticLockingFailureException e) {
            return upserter.removeMissing(gone, actorId);
        }
    }

    /** Cover sampling failures do not fail the catalog sync. */
    private void sampleCovers(ExecutorService pool) {
        try {
            coverAmbience.fillMissing(pool);
        } catch (RuntimeException e) {
            log.warn("Catalog sync: the cover ambience pass did not finish", e);
        }
    }

    /** Reads changed objects with bounded concurrency using the import reader pool. */
    private List<Fetched> fetch(ExecutorService pool, List<CatalogObject> chunk,
            List<SkippedRow> skipped, Map<String, String> known) {

        List<CompletableFuture<Fetched>> futures = chunk.stream()
                .map(object -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String id = object.externalSourceIdHint();
                        String listedEtag = id == null ? null : known.get(id);
                        return new Fetched(object, store.readJson(object.key()), listedEtag);
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

    /** Records the run outcome in a separate transaction, including failed syncs. */
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

    /**
     * A fetched object with the ETag recorded when it was listed.
     * @param listedSourceEtag stored ETag, or null for new/forced reads; guards concurrent edits
     */
    record Fetched(CatalogObject object, String json, String listedSourceEtag) {

        String key() {
            return object.key();
        }
    }

    /** Current or last import progress; {@code running} controls P-06b polling. */
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

    /** What a sync would do, for a dry-run of the prefix. */
    public record PendingChanges(int listed, int newObjects, int changed, String source) {

        public int total() {
            return newObjects + changed;
        }

        public int unchanged() {
            return listed - total();
        }
    }
}
