package dev.servicelog.geo;

import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.customer.PropertyRepository;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PropertyGeocodingTest {

    @Autowired
    private PropertyGeocoding geocoding;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private PropertyRepository properties;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private Geocoder geocoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
    }

    private long addCustomerAt(String street) {
        Customer customer = new Customer("Pat Smith", "+14135551234", null, null);
        customer.addProperty(new Property(street, null, "Springfield", "MA", "01103", null));
        return customers.save(customer).getProperties().getFirst().getId();
    }

    @Test
    void savesWhereTheAddressIs() {
        long id = addCustomerAt("12 Oak St");
        when(geocoder.locate("12 Oak St, Springfield, MA 01103"))
                .thenReturn(Optional.of(new Coordinates(42.1021, -72.5912)));

        assertThat(geocoding.geocodePending()).isEqualTo(1);

        Property saved = properties.findById(id).orElseThrow();
        assertThat(saved.getLatitude()).isEqualTo(42.1021);
        assertThat(saved.getLongitude()).isEqualTo(-72.5912);
        assertThat(saved.getGeocodedAt()).isNotNull();
    }

    @Test
    void anAddressThatIsNotFoundIsNotLookedUpAgainEveryRound() {
        long id = addCustomerAt("1 Nowhere Rd");
        when(geocoder.locate(anyString())).thenReturn(Optional.empty());

        geocoding.geocodePending();
        geocoding.geocodePending();

        verify(geocoder, times(1)).locate(anyString());
        Property saved = properties.findById(id).orElseThrow();
        assertThat(saved.getLatitude()).isNull();
        assertThat(saved.getGeocodedAt()).isNotNull();
    }

    @Test
    void anAddressNotFoundDaysAgoIsTriedAgain() {
        long id = addCustomerAt("12 Oak St");
        Property property = properties.findById(id).orElseThrow();
        property.located(null, null, Instant.now().minus(PropertyGeocoding.RETRY_NOT_FOUND_AFTER).minusSeconds(60));
        properties.save(property);
        when(geocoder.locate(anyString())).thenReturn(Optional.of(new Coordinates(42.1, -72.5)));

        assertThat(geocoding.geocodePending()).isEqualTo(1);

        assertThat(properties.findById(id).orElseThrow().getLatitude()).isEqualTo(42.1);
    }

    @Test
    void whenTheServiceDoesNotAnswerTheAddressStaysPending() {
        long id = addCustomerAt("12 Oak St");
        when(geocoder.locate(anyString())).thenThrow(new GeocoderUnavailableException("down", null));

        assertThat(geocoding.geocodePending()).isZero();

        assertThat(properties.findById(id).orElseThrow().getGeocodedAt()).isNull();
    }

    @Test
    void changingTheAddressLooksItUpAgain() {
        long id = addCustomerAt("12 Oak St");
        when(geocoder.locate(anyString())).thenReturn(Optional.of(new Coordinates(42.1, -72.5)));
        geocoding.geocodePending();

        Property property = properties.findById(id).orElseThrow();
        property.update("40 Elm St", null, "Springfield", "MA", "01103", "Side door");
        properties.save(property);

        Property changed = properties.findById(id).orElseThrow();
        assertThat(changed.getLatitude()).isNull();
        assertThat(changed.getGeocodedAt()).isNull();
    }

    @Test
    void editingOnlyTheNotesKeepsTheLocation() {
        long id = addCustomerAt("12 Oak St");
        when(geocoder.locate(anyString())).thenReturn(Optional.of(new Coordinates(42.1, -72.5)));
        geocoding.geocodePending();

        Property property = properties.findById(id).orElseThrow();
        property.update("12 Oak St", "Apt 2", "Springfield", "MA", "01103", "Gate code 1234");
        properties.save(property);

        assertThat(properties.findById(id).orElseThrow().getLatitude()).isEqualTo(42.1);
    }
}
