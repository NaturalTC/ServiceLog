package dev.servicelog.customer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"4135551234", "(413) 555-1234", "413.555.1234", "413 555 1234", "+1 413 555 1234",
            "1-413-555-1234"})
    void commonFormats_normalizeToTheSameNumber(String input) {
        assertEquals("+14135551234", PhoneNumbers.normalize(input).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "555-1234", "41355512345", "0135551234", "4131551234", "phone", "+44 20 7946 0958"})
    void invalidNumbers_areRejected(String input) {
        assertTrue(PhoneNumbers.normalize(input).isEmpty());
    }
}
