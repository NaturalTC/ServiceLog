package dev.servicelog.equipment;

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

@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id")
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentType type;

    private String brand;

    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "install_date")
    private LocalDate installDate;

    private String notes;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected Equipment() {
        // for JPA
    }

    public Equipment(Property property, EquipmentType type, String brand, String model, String serialNumber,
                     LocalDate installDate, String notes) {
        this.property = property;
        update(type, brand, model, serialNumber, installDate, notes);
    }

    public void update(EquipmentType type, String brand, String model, String serialNumber, LocalDate installDate,
                       String notes) {
        this.type = type;
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.installDate = installDate;
        this.notes = notes;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Property getProperty() { return property; }
    public EquipmentType getType() { return type; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getSerialNumber() { return serialNumber; }
    public LocalDate getInstallDate() { return installDate; }
    public String getNotes() { return notes; }
}
