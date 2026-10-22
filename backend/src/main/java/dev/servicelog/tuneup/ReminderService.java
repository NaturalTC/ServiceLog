package dev.servicelog.tuneup;

import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.exception.ConflictException;
import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.exception.NotFoundException;
import dev.servicelog.tuneup.TuneUpDtos.DueUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Sends a tune-up reminder for one address, only when the owner asks (S11). Every attempt
// is logged, including failures. Deliberately not one transaction: a failed send must
// still leave its log entry behind.
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    static final Duration RECENT = Duration.ofDays(14);

    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final TuneUpService tuneUps;
    private final TuneUpReminderRepository reminders;
    private final ReminderTemplate template;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final String from;

    public ReminderService(CustomerRepository customers, AppUserRepository users, TuneUpService tuneUps,
                           TuneUpReminderRepository reminders, ReminderTemplate template,
                           JavaMailSender mailSender, Clock clock,
                           @Value("${app.mail.from}") String from) {
        this.customers = customers;
        this.users = users;
        this.tuneUps = tuneUps;
        this.reminders = reminders;
        this.template = template;
        this.mailSender = mailSender;
        this.clock = clock;
        this.from = from;
    }

    public TuneUpReminder send(long customerId, long propertyId, boolean resend) {
        Customer customer = customers.findWithPropertiesById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
        Property property = customer.getProperties().stream()
                .filter(p -> p.getId().equals(propertyId))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("That address doesn't belong to this customer"));
        if (customer.getEmail() == null) {
            throw new ConflictException("There's no email on file for " + customer.getName() + ".");
        }

        List<DueUnit> due = tuneUps.due().units().stream()
                .filter(u -> u.propertyId().equals(propertyId))
                .toList();
        if (due.isEmpty()) {
            throw new ConflictException("Nothing at this address is due for a tune-up right now.");
        }

        // Guard against double-sending: the owner has to confirm a second reminder
        Instant now = Instant.now(clock);
        reminders.findFirstByPropertyIdAndStatusOrderBySentAtDesc(propertyId, TuneUpReminder.Status.SENT)
                .filter(last -> last.getSentAt().isAfter(now.minus(RECENT)))
                .filter(last -> !resend)
                .ifPresent(last -> {
                    throw new RecentlyRemindedException(last.getSentAt());
                });

        ReminderTemplate.Email email = template.build(customer.getName(), due.get(0).address(), due);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(customer.getEmail());
        // Replies go straight to the owner
        users.findAll().stream().findFirst().ifPresent(owner -> message.setReplyTo(owner.getEmail()));
        message.setSubject(email.subject());
        message.setText(email.body());

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Tune-up reminder to customer id={} failed", customerId, e);
            reminders.save(new TuneUpReminder(customerId, propertyId, customer.getEmail(), email.subject(),
                    email.body(), TuneUpReminder.Status.FAILED, truncate(e.getMessage()), now));
            throw new ReminderFailedException();
        }
        log.info("Tune-up reminder sent to customer id={}", customerId);
        return reminders.save(new TuneUpReminder(customerId, propertyId, customer.getEmail(), email.subject(),
                email.body(), TuneUpReminder.Status.SENT, null, now));
    }

    @Transactional(readOnly = true)
    public List<TuneUpReminder> history(long customerId) {
        return reminders.findByCustomerIdOrderBySentAtDesc(customerId);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    // 409 with the date of the last reminder, so the app can ask "send again?"
    public static class RecentlyRemindedException extends RuntimeException {
        private final Instant lastSentAt;

        RecentlyRemindedException(Instant lastSentAt) {
            super("A reminder already went to this address recently.");
            this.lastSentAt = lastSentAt;
        }

        public Instant getLastSentAt() {
            return lastSentAt;
        }
    }

    public static class ReminderFailedException extends RuntimeException {
        ReminderFailedException() {
            super("The email couldn't be sent. It's been logged; try again in a few minutes.");
        }
    }
}
