package com.funix.swp490x.mrs.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns scheduling on only for the catalog poller.
 *
 * <p>Here rather than on the application class so a {@code @WebMvcTest} slice,
 * which imports configurations explicitly, never starts a scheduler.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "mrs.catalog.sync.enabled", havingValue = "true")
public class SchedulingConfig {
}
