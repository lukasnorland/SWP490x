package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.funix.swp490x.mrs.domain.CatalogImportRun;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.CatalogImportRunRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

/**
 * The sync contract, driven through the real object store, mapper and upsert
 * logic over real staged JSON. Only the persistence boundary is faked, by an
 * in-memory map standing in for the tables, which keeps the interesting part —
 * which objects a run decides to read — under test without needing MySQL.
 */
class CatalogImportServiceTest {

    private static final String ICE_CREAM = "003c5571-5014-387b-978c-2836125178a4.json";
    private static final String SECOND = "003e518f-2a19-4066-beca-051dc2cd6f0a.json";
    private static final String THIRD = "2f02e814-96a7-4aa8-9a6b-9d8ab2ac0a30.json";

    @TempDir
    private Path staged;

    /** Stands in for the {@code song} table, keyed as its unique index is. */
    private final Map<String, Song> songs = new LinkedHashMap<>();
    private final Map<String, Tag> tags = new HashMap<>();
    private final List<CatalogImportRun> runs = new ArrayList<>();

    private CatalogImportService service;
    private CountingStore store;
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() throws IOException {
        copyFixture(ICE_CREAM);
        copyFixture(SECOND);
        copyFixture(THIRD);

        store = new CountingStore(new LocalDirectoryCatalogObjectStore(staged));

        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willAnswer(invocation ->
                songs.values().stream()
                        .map(song -> new Object[] {song.getExternalSourceId(), song.getSourceEtag()})
                        .toList());
        given(songRepository.findIdAndExternalIdPairs()).willAnswer(invocation ->
                songs.values().stream()
                        .map(song -> new Object[] {song.getId(), song.getExternalSourceId()})
                        .toList());
        given(songRepository.findIdsByExternalSourceIdIn(any())).willReturn(List.of());
        given(songRepository.deleteByExternalSourceIdIn(any())).willAnswer(invocation -> {
            Collection<String> extIds = invocation.getArgument(0);
            int removed = 0;
            var iterator = songs.entrySet().iterator();
            while (iterator.hasNext()) {
                if (extIds.contains(iterator.next().getValue().getExternalSourceId())) {
                    iterator.remove();
                    removed++;
                }
            }
            return removed;
        });
        given(songRepository.findBySourceProviderAndExternalSourceId(any(), any()))
                .willAnswer(invocation -> Optional.ofNullable(
                        songs.get(key(invocation.getArgument(0), invocation.getArgument(1)))));
        given(songRepository.save(any(Song.class))).willAnswer(invocation -> {
            Song song = invocation.getArgument(0);
            songs.put(key(song.getSourceProvider(), song.getExternalSourceId()), song);
            return song;
        });

        tagRepository = mock(TagRepository.class);
        given(tagRepository.findByTypeAndName(any(), any())).willAnswer(invocation ->
                Optional.ofNullable(tags.get(
                        invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tags.put(tag.getType() + ":" + tag.getName(), tag);
            return tag;
        });
        given(tagRepository.deleteUnused()).willReturn(0);

        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class))).willAnswer(invocation -> {
            CatalogImportRun run = invocation.getArgument(0);
            if (!runs.contains(run)) {
                runs.add(run);
            }
            return run;
        });

        SongUpserter upserter = new SongUpserter(songRepository, tagRepository,
                new SongJsonMapper(), TestCatalogProviders.stub());
        service = new CatalogImportService(store, songRepository, runRepository,
                mock(AuditLogRepository.class), upserter, mock(CoverAmbienceService.class));
    }

    @Test
    void firstRunAddsEveryStagedSong() {
        ImportSummary summary = sync();

        assertThat(summary.listed()).isEqualTo(3);
        assertThat(summary.read()).isEqualTo(3);
        assertThat(summary.added()).isEqualTo(3);
        assertThat(summary.updated()).isZero();
        assertThat(summary.skippedRows()).isEmpty();
        assertThat(songs).hasSize(3);
        assertThat(service.isRunning()).isFalse();
        assertThat(service.progress().running()).isFalse();
        assertThat(service.progress().phase()).isEqualTo("done");
        assertThat(service.progress().added()).isEqualTo(3);
        assertThat(service.progress().percent()).isEqualTo(100);
        verify(tagRepository).deleteUnused();
    }

    /**
     * The claim the scheduled poller rests on: polling an unchanged prefix reads
     * no object bodies and writes nothing.
     */
    @Test
    void anUnchangedSecondRunReadsNothingAndWritesNothing() {
        sync();
        store.reads.clear();

        ImportSummary summary = sync();

        assertThat(summary.listed()).isEqualTo(3);
        assertThat(summary.read()).isZero();
        assertThat(summary.added()).isZero();
        assertThat(summary.updated()).isZero();
        assertThat(summary.isNoChange()).isTrue();
        assertThat(store.reads).isEmpty();
    }

    /** Re-uploading a corrected JSON for a song already in MySQL. */
    @Test
    void editingOneObjectUpdatesOnlyThatSong() throws IOException {
        sync();
        store.reads.clear();
        rewriteTitle(ICE_CREAM, "Ice Cream (Remastered)");

        ImportSummary summary = sync();

        assertThat(summary.read()).isEqualTo(1);
        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.added()).isZero();
        assertThat(store.reads).containsExactly(ICE_CREAM);
        assertThat(songs.values())
                .extracting(Song::getTitle)
                .contains("Ice Cream (Remastered)");
    }

    @Test
    void addingOneObjectAddsOnlyThatSong() throws IOException {
        sync();
        store.reads.clear();
        write("new-song.json", """
                {"externalSourceId":"new-song","sourceProvider":"NCS",
                 "title":"Fresh Upload","genres":["Pop"]}
                """);

        ImportSummary summary = sync();

        assertThat(summary.listed()).isEqualTo(4);
        assertThat(summary.read()).isEqualTo(1);
        assertThat(summary.added()).isEqualTo(1);
        assertThat(store.reads).containsExactly("new-song.json");
    }

    @Test
    void removingAStagedObjectDeletesTheSong() throws IOException {
        sync();
        Files.delete(staged.resolve(ICE_CREAM));
        store.reads.clear();

        ImportSummary summary = sync();

        assertThat(summary.removed()).isEqualTo(1);
        assertThat(summary.added()).isZero();
        assertThat(summary.updated()).isZero();
        assertThat(songs).hasSize(2);
        assertThat(songs.values())
                .extracting(Song::getExternalSourceId)
                .doesNotContain("003c5571-5014-387b-978c-2836125178a4");
    }

    @Test
    void anEmptyListingDoesNotWipeTheCatalog() throws IOException {
        sync();
        try (var files = Files.list(staged)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }

        ImportSummary summary = sync();

        assertThat(summary.listed()).isZero();
        assertThat(summary.removed()).isZero();
        assertThat(songs).hasSize(3);
    }

    @Test
    void forceReReadsEverythingEvenWhenNothingChanged() {
        sync();
        store.reads.clear();

        ImportSummary summary = service.sync(ImportTrigger.MANUAL, 1L, true);

        assertThat(summary.read()).isEqualTo(3);
        assertThat(summary.updated()).isEqualTo(3);
        assertThat(summary.added()).isZero();
        assertThat(store.reads).hasSize(3);
    }

    /** SC-05: the object is read, then rejected with a reason. */
    @Test
    void skipsAnUnregisteredProvider() throws IOException {
        write("stranger.json", """
                {"externalSourceId":"stranger","sourceProvider":"SomeLabel","title":"T"}
                """);

        ImportSummary summary = sync();

        assertThat(summary.added()).isEqualTo(3);
        assertThat(summary.skippedRows())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.key()).isEqualTo("stranger.json");
                    assertThat(row.reason()).contains("unregistered provider");
                });
    }

    /**
     * A skipped object stores no ETag, so it is offered again rather than being
     * quietly forgotten — the same property that makes a failed chunk resume.
     */
    @Test
    void aSkippedObjectIsRetriedByTheNextRun() throws IOException {
        write("stranger.json", """
                {"externalSourceId":"stranger","sourceProvider":"SomeLabel","title":"T"}
                """);
        sync();
        store.reads.clear();

        ImportSummary summary = sync();

        assertThat(store.reads).containsExactly("stranger.json");
        assertThat(summary.skippedRows()).hasSize(1);
    }

    /** A run is recorded either way, so P-06b can always show a timestamp. */
    @Test
    void recordsEveryRunWithItsCounts() {
        sync();

        assertThat(runs).hasSize(1);
        CatalogImportRun run = runs.getFirst();
        assertThat(run.getTriggerType()).isEqualTo(ImportTrigger.MANUAL);
        assertThat(run.getObjectsListed()).isEqualTo(3);
        assertThat(run.getAdded()).isEqualTo(3);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.isFailed()).isFalse();
    }

    /**
     * The songs are already committed by then, so a failing audit write must not
     * report a successful import as broken.
     */
    @Test
    void anImportStillSucceedsWhenTheAuditWriteFails() {
        AuditLogRepository auditLog = mock(AuditLogRepository.class);
        given(auditLog.save(any())).willThrow(new IllegalStateException("audit is down"));
        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willReturn(List.of());
        given(songRepository.findIdAndExternalIdPairs()).willReturn(List.of());
        given(songRepository.findBySourceProviderAndExternalSourceId(any(), any()))
                .willReturn(Optional.empty());
        given(songRepository.save(any(Song.class))).willAnswer(invocation -> invocation.getArgument(0));
        TagRepository tags = mock(TagRepository.class);
        given(tags.findByTypeAndName(any(), any())).willReturn(Optional.empty());
        given(tags.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(tags.deleteUnused()).willReturn(0);
        SongUpserter upserter = new SongUpserter(songRepository, tags,
                new SongJsonMapper(), TestCatalogProviders.stub());
        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ImportSummary summary = new CatalogImportService(store, songRepository, runRepository,
                auditLog, upserter, mock(CoverAmbienceService.class))
                .sync(ImportTrigger.MANUAL, 1L, false);

        assertThat(summary.isFailed()).isFalse();
        assertThat(summary.added()).isEqualTo(3);
    }

    /** A store that cannot be listed must be reported, not thrown at the user. */
    @Test
    void recordsAFailedRunWhenTheStoreIsUnreachable() {
        CatalogObjectStore broken = mock(CatalogObjectStore.class);
        given(broken.list()).willThrow(new CatalogStoreException("bucket is gone"));
        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willReturn(List.of());

        CatalogImportService failing = new CatalogImportService(broken, songRepository,
                runRepository, mock(AuditLogRepository.class), mock(SongUpserter.class),
                mock(CoverAmbienceService.class));

        ImportSummary summary = failing.sync(ImportTrigger.SCHEDULED, null, false);

        assertThat(summary.isFailed()).isTrue();
        assertThat(summary.error()).contains("bucket is gone");
    }

    /** BR-06 through the sync: a conflicted row is reported, never forced. */
    @Test
    void reportsRowsTheUpsertRefused() {
        SongUpserter refusing = mock(SongUpserter.class);
        willAnswer(invocation -> {
            List<CatalogImportService.Fetched> chunk = invocation.getArgument(0);
            return new SongUpserter.ChunkResult(0, 0, chunk.stream()
                    .map(f -> new ImportSummary.SkippedRow(f.key(), "version conflict"))
                    .toList());
        }).given(refusing).upsertChunk(anyList());

        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willReturn(List.of());
        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ImportSummary summary = new CatalogImportService(store, songRepository, runRepository,
                mock(AuditLogRepository.class), refusing, mock(CoverAmbienceService.class))
                .sync(ImportTrigger.MANUAL, 1L, false);

        assertThat(summary.added()).isZero();
        assertThat(summary.skipped()).isEqualTo(3);
        assertThat(summary.skippedRows()).allSatisfy(row ->
                assertThat(row.reason()).isEqualTo("version conflict"));
    }

    @Test
    void pendingChangesReportsWhatARunWouldDoWithoutChangingAnything() throws IOException {
        sync();
        rewriteTitle(ICE_CREAM, "Ice Cream (Remastered)");
        write("new-song.json", """
                {"externalSourceId":"new-song","sourceProvider":"NCS","title":"Fresh"}
                """);
        store.reads.clear();

        CatalogImportService.PendingChanges pending = service.pendingChanges();

        assertThat(service.sourceDescription()).isEqualTo(staged.toAbsolutePath().toString());
        assertThat(pending.listed()).isEqualTo(4);
        assertThat(pending.newObjects()).isEqualTo(1);
        assertThat(pending.changed()).isEqualTo(1);
        assertThat(pending.unchanged()).isEqualTo(2);
        assertThat(store.reads).as("a preview must not download anything").isEmpty();
    }

    @Test
    void pendingChangesWrapsAnExpiredAwsLoginAsCatalogStoreException() {
        CatalogObjectStore broken = mock(CatalogObjectStore.class);
        given(broken.list()).willThrow(new IllegalStateException(
                "Failed to refresh process-based credentials."));
        given(broken.describe()).willReturn("s3://mrs-assets/song-data/");

        CatalogImportService failing = new CatalogImportService(broken, mock(SongRepository.class),
                mock(CatalogImportRunRepository.class), mock(AuditLogRepository.class),
                mock(SongUpserter.class), mock(CoverAmbienceService.class));

        assertThatThrownBy(failing::pendingChanges)
                .isInstanceOf(CatalogStoreException.class)
                .hasMessageContaining("Failed to refresh process-based credentials");
    }

    /** Songs staged with metadata always land tagged, which DC-03 requires. */
    @Test
    void resolvesTagsOnceAndSharesThemBetweenSongs() {
        sync();

        assertThat(songs.values()).allSatisfy(song ->
                assertThat(song.getTags()).isNotEmpty());
        // "Pop" recurs across the fixtures but must exist as a single tag.
        assertThat(tags.keySet()).contains(TagType.GENRE + ":Pop");
        assertThat(tags.keySet().stream().filter(k -> k.equals(TagType.GENRE + ":Pop")).count())
                .isEqualTo(1);
    }

    @Test
    void storesTheContentHashAlongsideEachSong() {
        sync();

        assertThat(songs.values()).allSatisfy(song ->
                assertThat(song.getSourceEtag()).isNotBlank());
    }

    @Test
    void startAsyncAppliesTheCatalogOffTheCallerThread() throws Exception {
        assertThat(service.startAsync(ImportTrigger.MANUAL, 1L, false)).isTrue();
        waitUntilIdle(service);
        assertThat(songs).hasSize(3);
        assertThat(service.progress().phase()).isEqualTo("done");
        assertThat(service.progress().added()).isEqualTo(3);
    }

    @Test
    void sync_whenEntryLacksTitle_shouldSkipOnlyThatEntry() throws IOException {
        write("no-title.json", """
                {"externalSourceId":"no-title","sourceProvider":"NCS"}
                """);

        ImportSummary summary = sync();

        assertThat(summary.added()).isEqualTo(3);
        assertThat(summary.skippedRows()).anySatisfy(row -> {
            assertThat(row.key()).contains("no-title");
            assertThat(row.reason()).contains("title");
        });
        assertThat(songs).hasSize(3);
    }

    @Test
    void startAsync_whenRunInProgress_shouldRefuse() throws Exception {
        CountDownLatch listed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CatalogObjectStore blocking = mock(CatalogObjectStore.class);
        given(blocking.list()).willAnswer(invocation -> {
            listed.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
        given(blocking.describe()).willReturn("blocked");
        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willReturn(List.of());
        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CatalogImportService blocked = new CatalogImportService(blocking, songRepository,
                runRepository, mock(AuditLogRepository.class), mock(SongUpserter.class),
                mock(CoverAmbienceService.class));

        assertThat(blocked.startAsync(ImportTrigger.MANUAL, 1L, false)).isTrue();
        assertThat(listed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(blocked.startAsync(ImportTrigger.MANUAL, 1L, false)).isFalse();
        release.countDown();
        waitUntilIdle(blocked);
    }

    @Test
    void startAsync_whenRunFails_shouldRecordFailure() throws Exception {
        CatalogObjectStore broken = mock(CatalogObjectStore.class);
        given(broken.list()).willThrow(new CatalogStoreException("bucket is gone"));
        given(broken.describe()).willReturn("s3://gone");
        List<CatalogImportRun> recorded = new ArrayList<>();
        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class))).willAnswer(invocation -> {
            CatalogImportRun run = invocation.getArgument(0);
            recorded.add(run);
            return run;
        });
        given(runRepository.findFirstByOrderByStartedAtDesc()).willAnswer(invocation ->
                recorded.isEmpty() ? Optional.empty() : Optional.of(recorded.getLast()));

        CatalogImportService failing = new CatalogImportService(broken, mock(SongRepository.class),
                runRepository, mock(AuditLogRepository.class), mock(SongUpserter.class),
                mock(CoverAmbienceService.class));

        assertThat(failing.startAsync(ImportTrigger.MANUAL, 1L, false)).isTrue();
        waitUntilIdle(failing);

        assertThat(failing.progress().phase()).isEqualTo("failed");
        assertThat(failing.lastRun()).isPresent();
        assertThat(failing.lastRun().get().isFailed()).isTrue();
    }

    @Test
    void pendingChanges_whenStoreEmpty_shouldReportNothing() {
        CatalogObjectStore empty = mock(CatalogObjectStore.class);
        given(empty.list()).willReturn(List.of());
        given(empty.describe()).willReturn("empty");
        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willReturn(List.of());

        CatalogImportService.PendingChanges pending = new CatalogImportService(empty,
                songRepository, mock(CatalogImportRunRepository.class),
                mock(AuditLogRepository.class), mock(SongUpserter.class),
                mock(CoverAmbienceService.class)).pendingChanges();

        assertThat(pending.listed()).isZero();
        assertThat(pending.newObjects()).isZero();
        assertThat(pending.changed()).isZero();
    }

    private ImportSummary sync() {
        return service.sync(ImportTrigger.MANUAL, 1L, false);
    }

    private static void waitUntilIdle(CatalogImportService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (service.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(service.isRunning()).isFalse();
    }

    private void copyFixture(String name) throws IOException {
        write(name, new ClassPathResource("catalog/" + name)
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(staged.resolve(name), content, StandardCharsets.UTF_8);
    }

    private void rewriteTitle(String name, String title) throws IOException {
        String json = Files.readString(staged.resolve(name), StandardCharsets.UTF_8);
        write(name, json.replaceAll("\"title\"\\s*:\\s*\"[^\"]*\"",
                "\"title\": \"" + title + "\""));
    }

    private static String key(String provider, String externalId) {
        return provider + '\u0000' + externalId;
    }

    /** Records which objects were actually downloaded. */
    private static final class CountingStore implements CatalogObjectStore {

        private final CatalogObjectStore delegate;
        private final List<String> reads = Collections.synchronizedList(new ArrayList<>());

        private CountingStore(CatalogObjectStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<CatalogObject> list() {
            return delegate.list();
        }

        @Override
        public String readJson(String key) {
            reads.add(key);
            return delegate.readJson(key);
        }

        @Override
        public java.util.Optional<String> findJson(String key) {
            return delegate.findJson(key);
        }

        @Override
        public String putJson(String key, String json) {
            return delegate.putJson(key, json);
        }

        @Override
        public void deleteJson(String key) {
            delegate.deleteJson(key);
        }

        @Override
        public void putBinary(String key, String contentType, java.io.InputStream body, long length) {
            delegate.putBinary(key, contentType, body, length);
        }

        @Override
        public void deleteBinary(String key) {
            delegate.deleteBinary(key);
        }

        @Override
        public List<String> listKeys(String keyPrefix) {
            return delegate.listKeys(keyPrefix);
        }

        @Override
        public String stagingKey(String externalSourceId) {
            return delegate.stagingKey(externalSourceId);
        }

        @Override
        public String describe() {
            return delegate.describe();
        }
    }
}
