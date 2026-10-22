package dev.servicelog.tuneup;

import org.springframework.boot.context.properties.ConfigurationProperties;

// How the business signs its emails. Set per deployment (BUSINESS_NAME, BUSINESS_PHONE),
// so no real business details live in the code.
@ConfigurationProperties("servicelog.business")
public record BusinessInfo(String name, String phone) {
}
