package dev.servicelog.service;

import dev.servicelog.equipment.Equipment;
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

@Entity
@Table(name = "service_record")
public class ServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceKind kind;

    @Column(name = "work_done", nullable = false)
    private String workDone;

    @Column(name = "parts_used")
    private String partsUsed;

    private String notes;

    @Column(name = "follow_up_needed", nullable = false)
    private boolean followUpNeeded;

    @Column(name = "follow_up_note")
    private String followUpNote;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected ServiceRecord() {
        // for JPA
    }

    public ServiceRecord(Equipment equipment, LocalDate serviceDate, ServiceKind kind, String workDone,
                         String partsUsed, String notes, boolean followUpNeeded, String followUpNote) {
        this.equipment = equipment;
        update(serviceDate, kind, workDone, partsUsed, notes, followUpNeeded, followUpNote);
    }

    public void update(LocalDate serviceDate, ServiceKind kind, String workDone, String partsUsed, String notes,
                       boolean followUpNeeded, String followUpNote) {
        this.serviceDate = serviceDate;
        this.kind = kind;
        this.workDone = workDone;
        this.partsUsed = partsUsed;
        this.notes = notes;
        this.followUpNeeded = followUpNeeded;
        // A follow-up note only makes sense when a follow-up is needed
        this.followUpNote = followUpNeeded ? followUpNote : null;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Equipment getEquipment() { return equipment; }
    public LocalDate getServiceDate() { return serviceDate; }
    public ServiceKind getKind() { return kind; }
    public String getWorkDone() { return workDone; }
    public String getPartsUsed() { return partsUsed; }
    public String getNotes() { return notes; }
    public boolean isFollowUpNeeded() { return followUpNeeded; }
    public String getFollowUpNote() { return followUpNote; }
}
