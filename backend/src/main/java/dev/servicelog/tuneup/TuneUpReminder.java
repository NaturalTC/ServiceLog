package dev.servicelog.tuneup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// The log entry for one reminder email: who it went to, what it said, and whether it sent
@Entity
@Table(name = "tune_up_reminder")
public class TuneUpReminder {

    public enum Status { SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String error;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected TuneUpReminder() {
        // for JPA
    }

    public TuneUpReminder(Long customerId, Long propertyId, String recipientEmail, String subject, String body,
                          Status status, String error, Instant sentAt) {
        this.customerId = customerId;
        this.propertyId = propertyId;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.error = error;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public Long getPropertyId() { return propertyId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Status getStatus() { return status; }
    public String getError() { return error; }
    public Instant getSentAt() { return sentAt; }
}
