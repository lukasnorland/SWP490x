package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.funix.swp490x.mrs.domain.CatalogImportRun;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.CatalogImportRunRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * The poller exists only where it is wanted, and cannot overlap another run.
 */
class CatalogSyncJobTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StubImportService.class);

    /** A developer machine and the test suite must never poll S3. */
    @Test
    void theJobIsAbsentUnlessSyncIsEnabled() {
        contextRunner
                .withUserConfiguration(CatalogSyncJob.class)
                .run(context -> assertThat(context).doesNotHaveBean(CatalogSyncJob.class));
    }

    @Test
    void theJobIsRegisteredWhenSyncIsEnabled() {
        contextRunner
                .withPropertyValues("mrs.catalog.sync.enabled=true")
                .withUserConfiguration(CatalogSyncJob.class)
                .run(context -> assertThat(context).hasSingleBean(CatalogSyncJob.class));
    }

    /**
     * Scheduling stays on so the 12-month log purge can run. The poller is still
     * gated by {@code mrs.catalog.sync.enabled} on {@link CatalogSyncJob}.
     */
    @Test
    void schedulingIsEnabledEvenWhenThePollerIsOff() {
        contextRunner
                .withUserConfiguration(SchedulingConfig.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    /**
     * ADMIN pressing the button mid-poll must be refused rather than run a second
     * import over the same objects.
     */
    @Test
    void aSecondCallIsRefusedWhileTheFirstIsStillRunning() throws Exception {
        CountDownLatch listing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CatalogObjectStore blocking = mock(CatalogObjectStore.class);
        given(blocking.list()).willAnswer(invocation -> {
            listing.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        SongRepository songRepository = mock(SongRepository.class);
        given(songRepository.findExternalIdAndEtagPairs()).willReturn(List.of());
        CatalogImportRunRepository runRepository = mock(CatalogImportRunRepository.class);
        given(runRepository.save(any(CatalogImportRun.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CatalogImportService service = new CatalogImportService(blocking, songRepository,
                runRepository, mock(AuditLogRepository.class), mock(SongUpserter.class),
                mock(CoverAmbienceService.class));

        AtomicReference<ImportSummary> first = new AtomicReference<>();
        Thread poller = new Thread(() ->
                first.set(service.sync(ImportTrigger.SCHEDULED, null, false)));
        poller.start();

        assertThat(listing.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.isRunning()).isTrue();
        assertThat(service.progress().running()).isTrue();
        assertThat(service.progress().phase()).isEqualTo("listing");
        assertThat(service.startAsync(ImportTrigger.MANUAL, 1L, false)).isFalse();

        ImportSummary refused = service.sync(ImportTrigger.MANUAL, 1L, false);
        assertThat(refused.alreadyRunning()).isTrue();

        release.countDown();
        poller.join(5_000);
        assertThat(first.get().alreadyRunning()).isFalse();
        assertThat(service.isRunning()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    static class StubImportService {

        @Bean
        CatalogImportService catalogImportService() {
            return mock(CatalogImportService.class);
        }
    }
}
