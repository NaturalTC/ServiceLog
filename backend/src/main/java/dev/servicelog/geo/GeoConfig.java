package dev.servicelog.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// Map lookups for service addresses. Off with GEOCODING_ENABLED=false (and in tests, which
// never call the real service): addresses then simply have no coordinates.
@Configuration
@EnableScheduling
public class GeoConfig {

    @Bean
    public Geocoder geocoder(@Value("${servicelog.geocoding.enabled:true}") boolean enabled,
                             @Value("${servicelog.geocoding.base-url}") String baseUrl) {
        if (!enabled) {
            return address -> java.util.Optional.empty();
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return new CensusGeocoder(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build());
    }
}
