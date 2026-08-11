package com.adproject.common.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        // MySQL DATETIME(6) stores microseconds. Ticking at the same precision keeps
        // API responses, audit events, and values read back from the database identical.
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
