package dev.servicelog.tuneup;

import dev.servicelog.tuneup.TuneUpDtos.DueUnit;
import dev.servicelog.tuneup.TuneUpDtos.Season;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// The tune-up reminder email. Plain text: it reads like a note from the technician,
// works in every email app, and has nothing for spam filters to dislike.
@Component
public class ReminderTemplate {

    private final BusinessInfo business;

    public ReminderTemplate(BusinessInfo business) {
        this.business = business;
    }

    public record Email(String subject, String body) {
    }

    public Email build(String customerName, String address, List<DueUnit> units, String unsubscribeLink) {
        String season = units.get(0).season() == Season.HEATING ? "fall" : "spring";
        String firstName = customerName.trim().split("\\s+")[0];
        String equipment = units.stream()
                .map(u -> "  - " + u.equipmentLabel() + (u.brand() != null ? " (" + u.brand() + ")" : ""))
                .collect(Collectors.joining("\n"));
        String phone = business.phone() == null || business.phone().isBlank() ? "" : " " + business.phone();

        String body = """
                Hi %s,

                It's time for the %s tune-up on the equipment at %s:

                %s

                A yearly tune-up keeps it running safely and efficiently and catches small
                problems before they turn into a no-heat or no-cooling call.

                Reply to this email or call%s to pick a time that works for you.

                Thanks,
                %s

                --
                Don't want tune-up reminders? Unsubscribe: %s
                """.formatted(firstName, season, address, equipment, phone, business.name(), unsubscribeLink);
        return new Email("Time for your " + season + " tune-up", body);
    }
}
