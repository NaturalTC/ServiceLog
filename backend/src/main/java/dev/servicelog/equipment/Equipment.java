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
import java.util.Objects;

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

    @Column(name = "warranty_registered", nullable = false)
    private boolean warrantyRegistered;

    @Column(name = "warranty_registered_on")
    private LocalDate warrantyRegisteredOn;

    @Column(name = "warranty_ends_on")
    private LocalDate warrantyEndsOn;

    @Column(name = "extended_warranty_ends_on")
    private LocalDate extendedWarrantyEndsOn;

    @Column(name = "warranty_offer_made_on")
    private LocalDate warrantyOfferMadeOn;

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

    // A unit that isn't registered can't have a registration date
    public void updateWarranty(boolean registered, LocalDate registeredOn, LocalDate endsOn,
                               LocalDate extendedEndsOn) {
        // New end dates (e.g. a renewal) mean any earlier offer no longer applies
        if (!Objects.equals(endsOn, warrantyEndsOn) || !Objects.equals(extendedEndsOn, extendedWarrantyEndsOn)) {
            this.warrantyOfferMadeOn = null;
        }
        this.warrantyRegistered = registered;
        this.warrantyRegisteredOn = registered ? registeredOn : null;
        this.warrantyEndsOn = endsOn;
        this.extendedWarrantyEndsOn = extendedEndsOn;
    }

    public void markWarrantyOffered(LocalDate on) {
        this.warrantyOfferMadeOn = on;
    }

    // The last day the unit is covered: the extended warranty if there is one
    public LocalDate getCoverEndsOn() {
        if (extendedWarrantyEndsOn != null && (warrantyEndsOn == null || extendedWarrantyEndsOn.isAfter(warrantyEndsOn))) {
            return extendedWarrantyEndsOn;
        }
        return warrantyEndsOn;
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
    public boolean isWarrantyRegistered() { return warrantyRegistered; }
    public LocalDate getWarrantyRegisteredOn() { return warrantyRegisteredOn; }
    public LocalDate getWarrantyEndsOn() { return warrantyEndsOn; }
    public LocalDate getExtendedWarrantyEndsOn() { return extendedWarrantyEndsOn; }
    public LocalDate getWarrantyOfferMadeOn() { return warrantyOfferMadeOn; }
}
