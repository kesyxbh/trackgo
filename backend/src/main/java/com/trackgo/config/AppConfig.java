package com.trackgo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Infrastructure beans shared by the whole application. */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * All "what time is it" questions go through this bean instead of {@code Instant.now()}, so
     * tests can replace it with a clock they control (see AnomalyIntegrationTest).
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
