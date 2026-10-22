package dev.servicelog.customer;

import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.security.JwtService;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CustomerSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    private String bearer;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        save("Pat Smith", "+14135551234", "12 Oak St", "Springfield");
        save("Robin Smith", "+14135559876", "88 Pine Ave", "Holyoke");
        save("Alex Adams", "+14137770000", "5 Oak Ridge Rd", "Westfield");
        Customer twoHomes = new Customer("Sam Lee", "+14134440000", null, null);
        twoHomes.addProperty(new Property("1 Maple Rd", null, "Agawam", "MA", "01001", null));
        twoHomes.addProperty(new Property("2 Maple Ct", null, "Agawam", "MA", "01001", null));
        customers.save(twoHomes);
    }

    @Test
    void byName_isCaseInsensitive() throws Exception {
        search("SMITH").andExpect(jsonPath("$[*].name", contains("Pat Smith", "Robin Smith")));
    }

    @Test
    void byPartOfAName() throws Exception {
        search("rob").andExpect(jsonPath("$[*].name", contains("Robin Smith")));
    }

    @Test
    void byStreet() throws Exception {
        search("oak").andExpect(jsonPath("$[*].name", containsInAnyOrder("Pat Smith", "Alex Adams")));
    }

    @Test
    void customerWithTwoMatchingStreets_appearsOnce() throws Exception {
        search("maple").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("Sam Lee"));
    }

    @Test
    void byPhone_inAnyFormat() throws Exception {
        for (String q : List.of("555-1234", "(413) 555-1234", "4135551234", "+1 413 555 1234")) {
            search(q).andExpect(jsonPath("$[*].name", contains("Pat Smith")));
        }
    }

    @Test
    void partialPhone_matchesEveryoneWithThoseDigits() throws Exception {
        search("413 555").andExpect(jsonPath("$[*].name", contains("Pat Smith", "Robin Smith")));
    }

    @Test
    void everyWordMustMatch() throws Exception {
        search("smith oak").andExpect(jsonPath("$[*].name", contains("Pat Smith")));
        search("smith pine").andExpect(jsonPath("$[*].name", contains("Robin Smith")));
    }

    @Test
    void resultsIncludeProperties() throws Exception {
        search("maple").andExpect(jsonPath("$[0].properties", hasSize(2)));
    }

    @Test
    void noMatch_returnsEmptyList() throws Exception {
        search("zzz").andExpect(jsonPath("$", empty()));
    }

    @Test
    void wildcardCharacters_areTreatedLiterally() throws Exception {
        search("%").andExpect(jsonPath("$", empty()));
        search("_").andExpect(jsonPath("$", empty()));
    }

    @Test
    void blankQuery_listsEveryone() throws Exception {
        search("   ").andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void search_requiresLogin() throws Exception {
        mockMvc.perform(get("/api/customers").param("q", "smith")).andExpect(status().isUnauthorized());
    }

    @Test
    void results_areCappedAt50() throws Exception {
        saveMany(60, "Jordan");

        search("jordan").andExpect(jsonPath("$", hasSize(CustomerService.MAX_SEARCH_RESULTS)));
    }

    @Test
    void searchStaysFast_with600Customers() throws Exception {
        saveMany(600, "Customer");

        search("customer 59").andExpect(status().isOk()); // warm up
        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            search("oak").andExpect(status().isOk());
            search("555").andExpect(status().isOk());
        }
        long averageMs = (System.nanoTime() - start) / 20 / 1_000_000;

        // Generous bound so slow CI machines don't flake; locally this is a few milliseconds
        assertTrue(averageMs < 250, "average search took " + averageMs + " ms");
    }

    private void save(String name, String phone, String street, String city) {
        Customer customer = new Customer(name, phone, null, null);
        customer.addProperty(new Property(street, null, city, "MA", "01103", null));
        customers.save(customer);
    }

    private void saveMany(int count, String namePrefix) {
        List<Customer> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Customer customer = new Customer(namePrefix + " " + i, "+1413%07d".formatted(2_000_000 + i), null, null);
            customer.addProperty(new Property(i + " Main St", null, "Springfield", "MA", "01103", null));
            batch.add(customer);
        }
        customers.saveAll(batch);
    }

    private ResultActions search(String q) throws Exception {
        return mockMvc.perform(get("/api/customers").param("q", q).header("Authorization", bearer))
                .andExpect(status().isOk());
    }
}
