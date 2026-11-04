package dev.servicelog.geo;

import java.util.Optional;

// Turns a street address into map coordinates. Empty when the address can't be found;
// GeocoderUnavailableException when the lookup service doesn't answer (try again later).
public interface Geocoder {

    Optional<Coordinates> locate(String address);
}
