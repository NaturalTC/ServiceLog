package dev.servicelog.job;

import dev.servicelog.customer.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "job")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id")
    private Property property;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobUrgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.SCHEDULED;

    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected Job() {
        // for JPA
    }

    public Job(Property property, LocalDate scheduledDate, LocalTime startTime, int durationMinutes, JobType type,
               JobUrgency urgency, String notes) {
        update(property, scheduledDate, startTime, durationMinutes, type, urgency, notes);
    }

    // Covers rescheduling too: changing the date or time of a job is just an update
    public void update(Property property, LocalDate scheduledDate, LocalTime startTime, int durationMinutes,
                       JobType type, JobUrgency urgency, String notes) {
        this.property = property;
        this.scheduledDate = scheduledDate;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.type = type;
        this.urgency = urgency;
        this.notes = notes;
    }

    public void markInProgress() {
        if (status != JobStatus.SCHEDULED) {
            throw new IllegalStateException("Only a scheduled job can be started");
        }
        status = JobStatus.IN_PROGRESS;
    }

    // Undo an accidental start: back to scheduled
    public void markScheduled() {
        if (status != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress job can go back to scheduled");
        }
        status = JobStatus.SCHEDULED;
    }

    public void complete(Instant when) {
        if (!status.isOpen()) {
            throw new IllegalStateException("Only a scheduled or in-progress job can be finished");
        }
        status = JobStatus.DONE;
        completedAt = when;
    }

    public void cancel() {
        if (!status.isOpen()) {
            throw new IllegalStateException("Only a scheduled or in-progress job can be cancelled");
        }
        status = JobStatus.CANCELLED;
    }

    public LocalDateTime start() {
        return LocalDateTime.of(scheduledDate, startTime);
    }

    public LocalDateTime end() {
        return start().plusMinutes(durationMinutes);
    }

    // Two jobs overlap if each starts before the other ends; back-to-back jobs don't overlap
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start().isBefore(otherEnd) && otherStart.isBefore(end());
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Property getProperty() { return property; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public JobType getType() { return type; }
    public JobUrgency getUrgency() { return urgency; }
    public JobStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Instant getCompletedAt() { return completedAt; }
}
