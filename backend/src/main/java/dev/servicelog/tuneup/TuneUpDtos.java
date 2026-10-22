package dev.servicelog.tuneup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TuneUpDtos {

    private TuneUpDtos() {
    }

    public enum Season { HEATING, COOLING }

    // One unit that's due, with who to call and where
    public record DueUnit(Long equipmentId, String equipmentLabel, String brand, String model, Season season,
                          LocalDate lastTuneUp, // null: no tune-up or install on record
                          Long customerId, String customerName, String customerPhone, String customerEmail,
                          Long propertyId, String address,
                          Long bookedJobId, LocalDate bookedDate, // a tune-up already scheduled there
                          Instant lastReminderAt, // the last reminder email sent to this address, if recent
                          boolean remindersOptedOut) { // the customer unsubscribed: call, don't email
    }

    public record DueResponse(LocalDate today, boolean heatingSeason, boolean coolingSeason, int intervalMonths,
                              List<DueUnit> units) {
    }
}
