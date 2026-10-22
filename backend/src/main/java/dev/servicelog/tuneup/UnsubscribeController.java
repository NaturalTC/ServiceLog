package dev.servicelog.tuneup;

import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

// Public (no login): customers unsubscribe from the link in a reminder email.
// POST only: email security scanners open every link in a message, so a GET that
// unsubscribed would unsubscribe people who never clicked anything.
@RestController
public class UnsubscribeController {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeController.class);

    private final UnsubscribeTokens tokens;
    private final CustomerRepository customers;
    private final Clock clock;

    public UnsubscribeController(UnsubscribeTokens tokens, CustomerRepository customers, Clock clock) {
        this.tokens = tokens;
        this.customers = customers;
        this.clock = clock;
    }

    public record UnsubscribeRequest(String token) {
    }

    // From the "Stop reminder emails" page
    @PostMapping(value = "/api/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> unsubscribe(@RequestBody UnsubscribeRequest request) {
        return unsubscribeWith(request.token());
    }

    // RFC 8058 one-click unsubscribe from the mail app's own button: the token is in the URL
    // (List-Unsubscribe header) and the body is "List-Unsubscribe=One-Click"
    @PostMapping(value = "/api/unsubscribe", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, String> oneClick(@RequestParam("token") String token) {
        return unsubscribeWith(token);
    }

    private Map<String, String> unsubscribeWith(String token) {
        long customerId = tokens.customerIdFrom(token)
                .orElseThrow(() -> new InvalidRequestException("This unsubscribe link isn't valid."));
        // Same answer whether or not the customer still exists: nothing to learn from a token
        customers.findById(customerId).ifPresent(customer -> {
            if (customer.optOutOfReminders(Instant.now(clock))) {
                customers.save(customer);
                log.info("Customer id={} unsubscribed from tune-up reminders", customerId);
            }
        });
        return Map.of("message", "You're unsubscribed. You won't get tune-up reminder emails anymore.");
    }
}
