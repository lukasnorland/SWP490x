package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Upload validates before staging, names the object from externalSourceId, and
 * auto-syncs only when at least one file was written.
 */
class CatalogUploadServiceTest {

    private static final String FIXTURE = "003c5571-5014-387b-978c-2836125178a4.json";

    @TempDir
    private Path staged;

    private CatalogImportService importService;
    private CatalogUploadService uploadService;
    private LocalDirectoryCatalogObjectStore store;

    @BeforeEach
    void setUp() {
        store = new LocalDirectoryCatalogObjectStore(staged);
        importService = mock(CatalogImportService.class);
        given(importService.sync(any(), any(), anyBoolean()))
                .willReturn(new ImportSummary(1, 1, 1, 0, List.of(), false, null));

        uploadService = new CatalogUploadService(store, new SongJsonMapper(),
                new CatalogProperties(), importService);
    }

    @Test
    void stagesAValidFixtureUnderItsExternalSourceIdThenSyncs() throws Exception {
        MockMultipartFile file = jsonFile("upload.json", fixture(FIXTURE));

        CatalogUploadService.UploadResult result = uploadService.upload(List.of(file), 7L);

        assertThat(result.uploaded()).isEqualTo(1);
        assertThat(result.rejected()).isEmpty();
        assertThat(Files.exists(staged.resolve(
                "003c5571-5014-387b-978c-2836125178a4.json"))).isTrue();
        then(importService).should().sync(ImportTrigger.MANUAL, 7L, false);
        assertThat(result.sync()).isNotNull();
        assertThat(result.sync().added()).isEqualTo(1);
    }

    @Test
    void rejectsMissingTitleBeforeAnyWrite() {
        MockMultipartFile file = jsonFile("bad.json", """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"  "}
                """);

        CatalogUploadService.UploadResult result = uploadService.upload(List.of(file), 1L);

        assertThat(result.uploaded()).isZero();
        assertThat(result.rejected()).singleElement().satisfies(row -> {
            assertThat(row.key()).isEqualTo("bad.json");
            assertThat(row.reason()).isEqualTo("missing title");
        });
        assertThat(staged).isEmptyDirectory();
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void rejectsAnUnregisteredProvider() {
        MockMultipartFile file = jsonFile("stranger.json", """
                {"externalSourceId":"x1","sourceProvider":"SomeLabel","title":"T"}
                """);

        CatalogUploadService.UploadResult result = uploadService.upload(List.of(file), 1L);

        assertThat(result.uploaded()).isZero();
        assertThat(result.rejected().getFirst().reason()).contains("unregistered provider");
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void rejectsUnparseableJson() {
        MockMultipartFile file = jsonFile("broken.json", "{not json");

        CatalogUploadService.UploadResult result = uploadService.upload(List.of(file), 1L);

        assertThat(result.uploaded()).isZero();
        assertThat(result.rejected().getFirst().reason()).contains("parsed");
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void rejectsANonJsonFilename() {
        MockMultipartFile file = new MockMultipartFile("files", "notes.txt",
                "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        CatalogUploadService.UploadResult result = uploadService.upload(List.of(file), 1L);

        assertThat(result.rejected().getFirst().reason()).isEqualTo("not a .json file");
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void aMixedBatchStagesOnlyTheValidFilesThenSyncsOnce() throws Exception {
        MockMultipartFile good = jsonFile("good.json", fixture(FIXTURE));
        MockMultipartFile bad = jsonFile("bad.json", """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider"}
                """);

        CatalogUploadService.UploadResult result =
                uploadService.upload(List.of(good, bad), 3L);

        assertThat(result.uploaded()).isEqualTo(1);
        assertThat(result.rejected()).hasSize(1);
        assertThat(Files.list(staged).count()).isEqualTo(1);
        then(importService).should().sync(ImportTrigger.MANUAL, 3L, false);
    }

    @Test
    void anEmptySelectionDoesNotTouchTheStoreOrSync() {
        CatalogUploadService.UploadResult result = uploadService.upload(List.of(), 1L);

        assertThat(result.isEmptySelection()).isTrue();
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void refusesABatchLargerThanTheCap() {
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < CatalogUploadService.MAX_FILES + 1; i++) {
            files.add(jsonFile("f" + i + ".json", """
                    {"externalSourceId":"id-%d","sourceProvider":"DemoProvider","title":"T"}
                    """.formatted(i)));
        }

        CatalogUploadService.UploadResult result = uploadService.upload(List.copyOf(files), 1L);

        assertThat(result.tooMany()).isTrue();
        assertThat(result.uploaded()).isZero();
        assertThat(staged).isEmptyDirectory();
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void passesANullActorThroughToSync() throws Exception {
        uploadService.upload(List.of(jsonFile("a.json", fixture(FIXTURE))), null);

        then(importService).should().sync(eq(ImportTrigger.MANUAL), isNull(), eq(false));
    }

    @Test
    void keepsTheStagingWhenSyncIsAlreadyRunning() throws Exception {
        given(importService.sync(any(), any(), anyBoolean())).willReturn(ImportSummary.refused());

        CatalogUploadService.UploadResult result =
                uploadService.upload(List.of(jsonFile("a.json", fixture(FIXTURE))), 1L);

        assertThat(result.uploaded()).isEqualTo(1);
        assertThat(result.sync().alreadyRunning()).isTrue();
        assertThat(Files.list(staged).count()).isEqualTo(1);
    }

    private static MockMultipartFile jsonFile(String name, String body) {
        return new MockMultipartFile("files", name, "application/json",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static String fixture(String name) throws Exception {
        return new ClassPathResource("catalog/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
