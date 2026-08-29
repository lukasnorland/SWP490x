package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.ImportTrigger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Draft upload validates the whole batch before any write, names media by
 * vendor slug, and emits JSON the existing mapper can read back.
 */
class SongDraftUploadServiceTest {

    @TempDir
    private Path staged;

    private CatalogImportService importService;
    private LocalDirectoryCatalogObjectStore store;
    private SongDraftUploadService uploadService;
    private AtomicInteger ids;

    @BeforeEach
    void setUp() {
        store = new LocalDirectoryCatalogObjectStore(staged);
        importService = mock(CatalogImportService.class);
        given(importService.startAsync(any(), any(), anyBoolean())).willReturn(true);
        ids = new AtomicInteger();
        Supplier<UUID> nextId = () -> new UUID(0L, ids.incrementAndGet());
        uploadService = new SongDraftUploadService(store, new SongJsonMapper(),
                new CatalogProperties(), importService, nextId);
    }

    @Test
    void stagesAudioCoverAndJsonUnderTheVendorThenSyncs() throws Exception {
        SongDraftForm draft = draft("NCS", "Shine", "track.mp3", "cover.jpg");
        draft.setArtist("Spektrem");
        draft.setDuration(255);
        draft.setBpm(128);
        draft.setGenres("Electronic");
        draft.setMoods("Energetic");
        draft.setTags("NCS");

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft), 7L);

        assertThat(result.uploaded()).isEqualTo(1);
        assertThat(result.rejected()).isEmpty();

        String id = new UUID(0L, 1L).toString();
        Path audio = staged.resolve("song-data/audio/ncs/" + id + ".mp3");
        Path cover = staged.resolve("song-data/artwork/ncs/" + id + ".jpg");
        Path json = staged.resolve(id + ".json");
        assertThat(audio).exists();
        assertThat(cover).exists();
        assertThat(json).exists();

        String body = Files.readString(json);
        assertThat(body).contains("\"isExplicit\"");
        assertThat(body).contains("\"NCS\"");
        assertThat(body).contains("/song-data/audio/ncs/" + id + ".mp3");
        assertThat(body).contains("/song-data/artwork/ncs/" + id + ".jpg");

        SongJsonMapper.Result mapped = new SongJsonMapper().map(body,
                List.of("EpidemicSound", "NCS", "OneOff"));
        assertThat(mapped.isRejected()).isFalse();
        assertThat(mapped.values().title()).isEqualTo("Shine");
        assertThat(mapped.values().artist()).isEqualTo("Spektrem");
        assertThat(mapped.values().duration()).isEqualTo(255);
        assertThat(mapped.values().explicit()).isFalse();

        then(importService).should().startAsync(ImportTrigger.MANUAL, 7L, false);
    }

    @Test
    void usesTheEpidemicFolderForEpidemicSound() {
        uploadService.upload(List.of(draft("EpidemicSound", "BALLING", "a.mp3", null)), 1L);

        String id = new UUID(0L, 1L).toString();
        assertThat(staged.resolve("song-data/audio/epidemic/" + id + ".mp3")).exists();
    }

    @Test
    void usesTheOneOffFolder() {
        uploadService.upload(List.of(draft("OneOff", "Cut", "a.wav", null)), 1L);

        String id = new UUID(0L, 1L).toString();
        assertThat(staged.resolve("song-data/audio/one-off/" + id + ".wav")).exists();
    }

    @Test
    void rejectsMissingTitleBeforeAnyWrite() {
        SongDraftForm draft = draft("NCS", "  ", "a.mp3", null);

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft), 1L);

        assertThat(result.uploaded()).isZero();
        assertThat(result.rejected().getFirst().reason()).isEqualTo("missing title");
        assertThat(Files.exists(staged.resolve("song-data"))).isFalse();
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void rejectsAnUnregisteredProviderBeforeAnyWrite() {
        SongDraftForm draft = draft("SomeLabel", "T", "a.mp3", null);

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft), 1L);

        assertThat(result.rejected().getFirst().reason()).contains("unregistered provider");
        assertThat(Files.exists(staged.resolve("song-data"))).isFalse();
    }

    @Test
    void rejectsUnknownGenresBeforeAnyWrite() {
        SongDraftForm draft = draft("NCS", "T", "a.mp3", null);
        draft.setGenres("Cinematic");

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft), 1L);

        assertThat(result.uploaded()).isZero();
        assertThat(result.rejected().getFirst().reason()).contains("Cinematic");
        assertThat(Files.exists(staged.resolve("song-data"))).isFalse();
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void rejectsANonAudioFilename() {
        SongDraftForm draft = draft("NCS", "T", "notes.txt", null);

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft), 1L);

        assertThat(result.rejected().getFirst().reason()).isEqualTo("not an audio file");
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void aMixedBatchWritesNothingWhenOneSongIsInvalid() {
        SongDraftForm good = draft("NCS", "Good", "a.mp3", null);
        SongDraftForm bad = draft("NCS", "", "b.mp3", null);

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(good, bad), 1L);

        assertThat(result.uploaded()).isZero();
        assertThat(result.rejected()).isNotEmpty();
        assertThat(Files.exists(staged.resolve("song-data"))).isFalse();
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void anEmptySelectionDoesNotTouchTheStoreOrSync() {
        SongDraftUploadService.MediaUploadResult result = uploadService.upload(List.of(), 1L);

        assertThat(result.isEmptySelection()).isTrue();
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void refusesABatchLargerThanTheCap() {
        List<SongDraftForm> drafts = new ArrayList<>();
        for (int i = 0; i < SongDraftUploadService.MAX_DRAFTS + 1; i++) {
            drafts.add(draft("NCS", "T" + i, "a" + i + ".mp3", null));
        }

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.copyOf(drafts), 1L);

        assertThat(result.tooMany()).isTrue();
        assertThat(result.uploaded()).isZero();
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void coverIsOptional() throws Exception {
        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft("NCS", "Bare", "a.mp3", null)), 1L);

        assertThat(result.uploaded()).isEqualTo(1);
        String id = new UUID(0L, 1L).toString();
        String json = Files.readString(staged.resolve(id + ".json"));
        assertThat(json).contains("\"coverUrl\" : null");
        assertThat(Files.exists(staged.resolve("song-data/artwork"))).isFalse();
    }

    @Test
    void rejectsAnOversizedCover() {
        byte[] huge = new byte[6 * 1024 * 1024];
        SongDraftForm draft = draft("NCS", "T", "a.mp3", null);
        draft.setCover(new MockMultipartFile("cover", "cover.jpg", "image/jpeg", huge));

        SongDraftUploadService.MediaUploadResult result =
                uploadService.upload(List.of(draft), 1L);

        assertThat(result.rejected().getFirst().reason()).contains("cover larger than");
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    private static SongDraftForm draft(String provider, String title, String audioName,
            String coverName) {
        SongDraftForm form = new SongDraftForm();
        form.setSourceProvider(provider);
        form.setTitle(title);
        form.setAudio(new MockMultipartFile("audio", audioName, "audio/mpeg",
                "id3".getBytes(StandardCharsets.UTF_8)));
        if (coverName != null) {
            form.setCover(new MockMultipartFile("cover", coverName, "image/jpeg",
                    new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        }
        return form;
    }
}
