package dev.servicelog.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

// A service address. Equipment (S4) will be recorded per property.
@Entity
@Table(name = "property")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private String street;

    private String unit;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String zip;

    private String notes;

    // Where it is on the map (dev.servicelog.geo), so the phone can tell whose house it's at.
    // Null until looked up; geocodedAt is set even when the lookup found nothing.
    private Double latitude;

    private Double longitude;

    @Column(name = "geocoded_at")
    private Instant geocodedAt;

    protected Property() {
        // for JPA
    }

    public Property(String street, String unit, String city, String state, String zip, String notes) {
        update(street, unit, city, state, zip, notes);
    }

    public void update(String street, String unit, String city, String state, String zip, String notes) {
        // A different address is a different place: look it up again
        if (!Objects.equals(street, this.street) || !Objects.equals(city, this.city)
                || !Objects.equals(state, this.state) || !Objects.equals(zip, this.zip)) {
            latitude = null;
            longitude = null;
            geocodedAt = null;
        }
        this.street = street;
        this.unit = unit;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.notes = notes;
    }

    // The map lookup's result; null coordinates mean the address wasn't found
    public void located(Double latitude, Double longitude, Instant when) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.geocodedAt = when;
    }

    // One line for the map lookup ("12 Oak St, Springfield, MA 01103"); the unit doesn't
    // move the building
    public String mapAddress() {
        return street + ", " + city + ", " + state + " " + zip;
    }

    void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getStreet() { return street; }
    public String getUnit() { return unit; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getZip() { return zip; }
    public String getNotes() { return notes; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Instant getGeocodedAt() { return geocodedAt; }
}
