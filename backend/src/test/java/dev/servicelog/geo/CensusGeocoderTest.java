package dev.servicelog.geo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CensusGeocoderTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsTheFirstMatchLongitudeAsXAndLatitudeAsY() {
        var response = json.readTree("""
                {"result": {"addressMatches": [
                  {"matchedAddress": "12 OAK ST, SPRINGFIELD, MA, 01103", "coordinates": {"x": -72.5912, "y": 42.1021}},
                  {"coordinates": {"x": -70.0, "y": 41.0}}
                ]}}
                """);
        assertThat(CensusGeocoder.parse(response)).contains(new Coordinates(42.1021, -72.5912));
    }

    @Test
    void noMatchIsEmpty() {
        assertThat(CensusGeocoder.parse(json.readTree("{\"result\": {\"addressMatches\": []}}"))).isEmpty();
        assertThat(CensusGeocoder.parse(json.readTree("{}"))).isEmpty();
        assertThat(CensusGeocoder.parse(null)).isEmpty();
    }
}
