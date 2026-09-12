package com.funix.swp490x.mrs.catalog;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns scheduling on for the catalog poller and the 12-month log purge.
 *
 * <p>Here rather than on the application class so a {@code @WebMvcTest} slice,
 * which imports configurations explicitly, never starts a scheduler. The
 * catalog poller itself stays behind {@code mrs.catalog.sync.enabled}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
