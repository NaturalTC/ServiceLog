package dev.servicelog.tuneup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Login required (see SecurityConfig)
@RestController
public class ReminderController {

    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    public record SendReminderRequest(@NotNull Long customerId, @NotNull Long propertyId, Boolean resend) {
    }

    public record ReminderResponse(Long id, Long customerId, Long propertyId, String recipientEmail, String subject,
                                   String body, TuneUpReminder.Status status, String error, Instant sentAt) {

        static ReminderResponse from(TuneUpReminder r) {
            return new ReminderResponse(r.getId(), r.getCustomerId(), r.getPropertyId(), r.getRecipientEmail(),
                    r.getSubject(), r.getBody(), r.getStatus(), r.getError(), r.getSentAt());
        }
    }

    // Sends one reminder email, only ever in response to the owner's tap
    @PostMapping("/api/tune-ups/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse send(@Valid @RequestBody SendReminderRequest request) {
        return ReminderResponse.from(reminders.send(request.customerId(), request.propertyId(),
                Boolean.TRUE.equals(request.resend())));
    }

    @GetMapping("/api/customers/{customerId}/reminders")
    public List<ReminderResponse> history(@PathVariable long customerId) {
        return reminders.history(customerId).stream().map(ReminderResponse::from).toList();
    }

    @ExceptionHandler(ReminderService.RecentlyRemindedException.class)
    public ResponseEntity<Map<String, Object>> recentlySent(ReminderService.RecentlyRemindedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", 409, "code", "REMINDER_RECENTLY_SENT", "message", ex.getMessage(),
                "lastSentAt", ex.getLastSentAt().toString()));
    }

    @ExceptionHandler(ReminderService.ReminderFailedException.class)
    public ResponseEntity<Map<String, Object>> failed(ReminderService.ReminderFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "status", 502, "code", "EMAIL_FAILED", "message", ex.getMessage()));
    }
}
