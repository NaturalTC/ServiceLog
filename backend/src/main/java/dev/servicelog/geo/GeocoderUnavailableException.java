package dev.servicelog.geo;

// The lookup service didn't answer: nothing is known about the address, so try again later
public class GeocoderUnavailableException extends RuntimeException {

    public GeocoderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
