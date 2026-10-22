package dev.servicelog.config;

import dev.servicelog.tuneup.TuneUpRules;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(TuneUpRules.class)
@EnableAsync // lets AuthMailer send emails in the background
public class AppConfig {

    // Injected wherever the current time matters, so tests can control it
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
