package dev.servicelog.tuneup;

import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.job.Job;
import dev.servicelog.job.JobRepository;
import dev.servicelog.job.JobType;
import dev.servicelog.job.JobUrgency;
import dev.servicelog.security.JwtService;
import dev.servicelog.servicerecord.ServiceKind;
import dev.servicelog.servicerecord.ServiceRecord;
import dev.servicelog.servicerecord.ServiceRecordRepository;
import dev.servicelog.support.DatabaseCleaner;
import dev.servicelog.tuneup.TuneUpDtos.DueUnit;
import dev.servicelog.tuneup.TuneUpDtos.Season;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Uses fixed dates so the results don't depend on what month the tests run in
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TuneUpServiceTest {

    private static final LocalDate FALL = LocalDate.of(2026, 10, 15);   // heating season
    private static final LocalDate SPRING = LocalDate.of(2026, 4, 15);  // cooling season
    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 15); // neither

    @Autowired
    private TuneUpService tuneUps;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private EquipmentRepository equipment;

    @Autowired
    private ServiceRecordRepository records;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    private Property oak;
    private Property elm;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        Customer smith = new Customer("Pat Smith", "+14135551234", "pat@example.com", null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        Customer adams = new Customer("Alex Adams", "+14135559876", null, null);
        adams.addProperty(new Property("5 Elm St", null, "Holyoke", "MA", "01040", null));
        oak = customers.save(smith).getProperties().get(0);
        elm = customers.save(adams).getProperties().get(0);
    }

    @Test
    void oilBurner_isDueInFall_whenLastTuneUpIs11OrMoreMonthsAgo() {
        Equipment overdue = unit(oak, EquipmentType.OIL_BURNER, null);
        record(overdue, LocalDate.of(2025, 10, 1), ServiceKind.TUNE_UP);
        Equipment justDone = unit(elm, EquipmentType.OIL_BURNER, null);
        record(justDone, LocalDate.of(2026, 9, 20), ServiceKind.TUNE_UP);

        List<DueUnit> due = tuneUps.due(FALL).units();

        assertEquals(List.of(overdue.getId()), ids(due));
        assertEquals(LocalDate.of(2025, 10, 1), due.get(0).lastTuneUp());
        assertEquals(Season.HEATING, due.get(0).season());
    }

    @Test
    void exactlyElevenMonths_isDue() {
        Equipment unit = unit(oak, EquipmentType.BOILER, null);
        record(unit, FALL.minusMonths(11), ServiceKind.TUNE_UP);

        assertEquals(1, tuneUps.due(FALL).units().size());
    }

    @Test
    void neverTunedUp_isDue_andListedFirst() {
        Equipment never = unit(oak, EquipmentType.FURNACE, null);
        Equipment old = unit(elm, EquipmentType.FURNACE, null);
        record(old, LocalDate.of(2024, 10, 1), ServiceKind.TUNE_UP);

        List<DueUnit> due = tuneUps.due(FALL).units();

        assertEquals(List.of(never.getId(), old.getId()), ids(due));
        assertNull(due.get(0).lastTuneUp());
    }

    @Test
    void repairs_dontCountAsTuneUps() {
        Equipment unit = unit(oak, EquipmentType.OIL_BURNER, null);
        record(unit, LocalDate.of(2025, 9, 1), ServiceKind.TUNE_UP);
        record(unit, LocalDate.of(2026, 2, 1), ServiceKind.REPAIR);

        assertEquals(1, tuneUps.due(FALL).units().size());
    }

    @Test
    void recentInstall_isNotDue_byRecordOrByInstallDate() {
        Equipment installRecord = unit(oak, EquipmentType.BOILER, null);
        record(installRecord, LocalDate.of(2026, 3, 1), ServiceKind.INSTALLATION);
        unit(elm, EquipmentType.FURNACE, LocalDate.of(2026, 5, 1)); // install date on the unit only

        assertTrue(tuneUps.due(FALL).units().isEmpty());
    }

    @Test
    void cooling_isDueInSpring_notInFall() {
        Equipment ac = unit(oak, EquipmentType.CENTRAL_AC, null);
        record(ac, LocalDate.of(2025, 4, 1), ServiceKind.TUNE_UP);

        assertTrue(tuneUps.due(FALL).units().isEmpty());
        List<DueUnit> spring = tuneUps.due(SPRING).units();
        assertEquals(List.of(ac.getId()), ids(spring));
        assertEquals(Season.COOLING, spring.get(0).season());
    }

    @Test
    void heatPumpsAndMiniSplits_areDueInEitherSeason() {
        Equipment heatPump = unit(oak, EquipmentType.HEAT_PUMP, null);
        Equipment head = unit(elm, EquipmentType.MINI_SPLIT_INDOOR, null);

        assertEquals(2, tuneUps.due(FALL).units().size());
        assertEquals(2, tuneUps.due(SPRING).units().size());
    }

    @Test
    void outOfSeason_nothingIsDue() {
        unit(oak, EquipmentType.OIL_BURNER, null);
        unit(elm, EquipmentType.CENTRAL_AC, null);

        var response = tuneUps.due(JANUARY);
        assertFalse(response.heatingSeason());
        assertFalse(response.coolingSeason());
        assertTrue(response.units().isEmpty());
    }

    @Test
    void waterHeaters_areNeverOnTheTuneUpList() {
        unit(oak, EquipmentType.WATER_HEATER, null);

        assertTrue(tuneUps.due(FALL).units().isEmpty());
    }

    @Test
    void bookedTuneUp_marksTheUnit_andMovesItToTheEnd() {
        Equipment booked = unit(oak, EquipmentType.OIL_BURNER, null);
        Equipment notBooked = unit(elm, EquipmentType.OIL_BURNER, null);
        record(notBooked, LocalDate.of(2025, 9, 1), ServiceKind.TUNE_UP); // less overdue than "never"
        Job job = jobs.save(new Job(oak, FALL.plusDays(3), LocalTime.of(9, 0), 60, JobType.TUNE_UP,
                JobUrgency.NORMAL, null));

        List<DueUnit> due = tuneUps.due(FALL).units();

        assertEquals(List.of(notBooked.getId(), booked.getId()), ids(due));
        assertEquals(job.getId(), due.get(1).bookedJobId());
        assertEquals(FALL.plusDays(3), due.get(1).bookedDate());
    }

    @Test
    void dueUnit_includesWhoToCallAndWhere() {
        unit(oak, EquipmentType.BOILER, null);

        DueUnit unit = tuneUps.due(FALL).units().get(0);
        assertEquals("Pat Smith", unit.customerName());
        assertEquals("+14135551234", unit.customerPhone());
        assertEquals("pat@example.com", unit.customerEmail());
        assertEquals("12 Oak St, Springfield, MA 01103", unit.address());
        assertEquals("Boiler", unit.equipmentLabel());
    }

    @Test
    void endpoint_requiresLogin_andReturnsTheList() throws Exception {
        mockMvc.perform(get("/api/tune-ups/due")).andExpect(status().isUnauthorized());

        AppUser owner = new AppUser("owner@example.com", "x");
        owner.markEmailVerified();
        users.save(owner);
        String bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());
        mockMvc.perform(get("/api/tune-ups/due").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalMonths").value(11))
                .andExpect(jsonPath("$.units").isArray());
    }

    private Equipment unit(Property property, EquipmentType type, LocalDate installDate) {
        return equipment.save(new Equipment(property, type, null, null, null, installDate, null));
    }

    private void record(Equipment unit, LocalDate date, ServiceKind kind) {
        records.save(new ServiceRecord(unit, date, kind, "work", null, null, false, null));
    }

    private static List<Long> ids(List<DueUnit> units) {
        return units.stream().map(DueUnit::equipmentId).toList();
    }
}
