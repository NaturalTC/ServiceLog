package dev.servicelog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@Configuration
@EnableAsync // lets AuthMailer send emails in the background
public class AppConfig {

    // Injected wherever the current time matters, so tests can control it
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
