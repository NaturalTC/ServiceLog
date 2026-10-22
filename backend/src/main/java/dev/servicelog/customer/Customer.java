package dev.servicelog.customer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    private String email;

    private String notes;

    // Set when the customer unsubscribes from tune-up reminder emails (S12)
    @Column(name = "reminders_opted_out_at")
    private Instant remindersOptedOutAt;

    // Properties are saved and deleted together with their customer
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Property> properties = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected Customer() {
        // for JPA
    }

    public Customer(String name, String phone, String email, String notes) {
        updateDetails(name, phone, email, notes);
    }

    public void updateDetails(String name, String phone, String email, String notes) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.notes = notes;
    }

    // Returns false if they had already opted out (keeps the original date)
    public boolean optOutOfReminders(Instant when) {
        if (remindersOptedOutAt != null) {
            return false;
        }
        remindersOptedOutAt = when;
        return true;
    }

    // Only when the customer asks the owner to start reminders again
    public void resumeReminders() {
        remindersOptedOutAt = null;
    }

    public void addProperty(Property property) {
        property.setCustomer(this);
        properties.add(property);
    }

    public void removeProperty(Property property) {
        properties.remove(property);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getNotes() { return notes; }
    public Instant getRemindersOptedOutAt() { return remindersOptedOutAt; }
    public List<Property> getProperties() { return Collections.unmodifiableList(properties); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
