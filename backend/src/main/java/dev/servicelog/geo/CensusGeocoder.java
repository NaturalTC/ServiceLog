package dev.servicelog.geo;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

// The US Census Bureau's free geocoder: no account or key, US addresses only (which is all
// a western-Mass shop has). https://geocoding.geo.census.gov/geocoder/
public class CensusGeocoder implements Geocoder {

    private final RestClient http;

    public CensusGeocoder(RestClient http) {
        this.http = http;
    }

    @Override
    public Optional<Coordinates> locate(String address) {
        try {
            JsonNode response = http.get()
                    .uri(u -> u.path("/locations/onelineaddress")
                            .queryParam("address", address)
                            .queryParam("benchmark", "Public_AR_Current")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response);
        } catch (RestClientException e) {
            throw new GeocoderUnavailableException("Map lookup didn't answer", e);
        }
    }

    // {"result": {"addressMatches": [{"coordinates": {"x": -72.59, "y": 42.10}, ...}]}};
    // x is longitude, y is latitude. The first match is the best one.
    static Optional<Coordinates> parse(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode coordinates = response.path("result").path("addressMatches").path(0).path("coordinates");
        if (!coordinates.path("x").isNumber() || !coordinates.path("y").isNumber()) {
            return Optional.empty();
        }
        return Optional.of(new Coordinates(coordinates.path("y").asDouble(), coordinates.path("x").asDouble()));
    }
}
