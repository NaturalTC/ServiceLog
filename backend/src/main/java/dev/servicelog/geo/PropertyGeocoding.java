package dev.servicelog.geo;

import dev.servicelog.customer.Property;
import dev.servicelog.customer.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Looks up new and changed service addresses on the map in the background, a few at a time
// (never while someone waits on a save). Existing addresses are filled in the same way the
// first time the server runs with this. An address the service didn't find is tried again
// after a few days, since the free service sometimes answers "no match" for a while.
@Service
public class PropertyGeocoding {

    private static final Logger log = LoggerFactory.getLogger(PropertyGeocoding.class);

    static final int PER_ROUND = 20;
    static final Duration RETRY_NOT_FOUND_AFTER = Duration.ofDays(3);
    private static final long PAUSE_BETWEEN_LOOKUPS_MS = 300; // gentle on a free service

    private final PropertyRepository properties;
    private final Geocoder geocoder;
    private final Clock clock;
    private final boolean enabled;

    public PropertyGeocoding(PropertyRepository properties, Geocoder geocoder, Clock clock,
                             @Value("${servicelog.geocoding.enabled:true}") boolean enabled) {
        this.properties = properties;
        this.geocoder = geocoder;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "${servicelog.geocoding.initial-delay-ms:20000}",
            fixedDelayString = "${servicelog.geocoding.interval-ms:60000}")
    public void run() {
        if (enabled) {
            geocodePending();
        }
    }

    // Returns how many addresses were found on the map this round
    public int geocodePending() {
        int found = 0;
        Instant now = Instant.now(clock);
        List<Property> pending = properties.findToGeocode(now.minus(RETRY_NOT_FOUND_AFTER), PageRequest.of(0, PER_ROUND));
        for (Property property : pending) {
            if (property != pending.getFirst()) {
                pause();
            }
            Optional<Coordinates> where;
            try {
                where = geocoder.locate(property.mapAddress());
            } catch (GeocoderUnavailableException e) {
                // Leave the rest for the next round rather than marking them not found
                log.warn("{}; trying again later", e.getMessage());
                break;
            }
            property.located(where.map(Coordinates::latitude).orElse(null),
                    where.map(Coordinates::longitude).orElse(null), Instant.now(clock));
            properties.save(property);
            if (where.isPresent()) {
                found++;
            } else {
                log.info("Address {} not found on the map", property.getId());
            }
        }
        return found;
    }

    private void pause() {
        try {
            Thread.sleep(PAUSE_BETWEEN_LOOKUPS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
