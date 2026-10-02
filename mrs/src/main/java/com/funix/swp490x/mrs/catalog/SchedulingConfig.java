package com.funix.swp490x.mrs.catalog;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables catalog sync and log retention jobs outside MVC test slices. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
