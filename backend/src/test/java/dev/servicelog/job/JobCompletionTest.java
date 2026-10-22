package dev.servicelog.job;

import com.jayway.jsonpath.JsonPath;
import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.security.JwtService;
import dev.servicelog.servicerecord.ServiceRecordRepository;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class JobCompletionTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/New_York"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private ServiceRecordRepository serviceRecords;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private JavaMailSender mailSender;

    private String bearer;
    private long oakStreet;
    private Equipment outdoorUnit;
    private Equipment indoorHead;
    private Equipment elmBoiler;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        Customer smith = new Customer("Pat Smith", "+14135551234", null, null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        smith.addProperty(new Property("5 Elm St", null, "Holyoke", "MA", "01040", null));
        smith = customers.save(smith);
        Property oak = smith.getProperties().get(0);
        oakStreet = oak.getId();
        outdoorUnit = equipmentRepository.save(new Equipment(oak, EquipmentType.MINI_SPLIT_OUTDOOR, "Mitsubishi",
                null, "SN-OUT", null, null));
        indoorHead = equipmentRepository.save(new Equipment(oak, EquipmentType.MINI_SPLIT_INDOOR, "Mitsubishi",
                null, "SN-IN", null, null));
        elmBoiler = equipmentRepository.save(new Equipment(smith.getProperties().get(1), EquipmentType.BOILER,
                "Burnham", null, null, null, null));
    }

    @Test
    void complete_marksDone_andCreatesARecordPerUnit_withKindFromJobType() throws Exception {
        long job = schedule(oakStreet, TODAY, "TUNE_UP");

        String body = complete(job, """
                {"equipmentIds": [%d, %d], "workDone": " Cleaned coils and filters ", "partsUsed": "Coil cleaner",
                 "followUpNeeded": true, "followUpNote": "Recheck drain in spring"}
                """.formatted(outdoorUnit.getId(), indoorHead.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("DONE"))
                .andExpect(jsonPath("$.job.completedAt", notNullValue()))
                .andExpect(jsonPath("$.serviceRecordIds", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        long recordId = ((Number) JsonPath.read(body, "$.serviceRecordIds[0]")).longValue();
        mockMvc.perform(get("/api/service-records/" + recordId).header("Authorization", bearer))
                .andExpect(jsonPath("$.record.kind").value("TUNE_UP"))
                .andExpect(jsonPath("$.record.workDone").value("Cleaned coils and filters"))
                .andExpect(jsonPath("$.record.serviceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.record.followUpNote").value("Recheck drain in spring"))
                .andExpect(jsonPath("$.record.jobId").value(job));
        mockMvc.perform(get("/api/equipment/" + indoorHead.getId() + "/service-records").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void serviceCall_createsARepairRecord_installCreatesAnInstallation() throws Exception {
        long repair = schedule(oakStreet, TODAY, "SERVICE_CALL");
        long install = schedule(oakStreet, TODAY.minusDays(1), "INSTALL");

        String r = complete(repair, work(outdoorUnit)).andReturn().getResponse().getContentAsString();
        String i = complete(install, work(indoorHead)).andReturn().getResponse().getContentAsString();

        assertEquals("REPAIR", kindOf(r));
        assertEquals("INSTALLATION", kindOf(i));
    }

    @Test
    void pastJob_isRecordedOnItsOwnDate_futureJobFinishedEarlyOnToday() throws Exception {
        long past = schedule(oakStreet, TODAY.minusDays(3), "TUNE_UP");
        long future = schedule(oakStreet, TODAY.plusDays(5), "TUNE_UP");

        String p = complete(past, work(outdoorUnit)).andReturn().getResponse().getContentAsString();
        String f = complete(future, work(indoorHead)).andReturn().getResponse().getContentAsString();

        assertEquals(TODAY.minusDays(3).toString(), dateOf(p));
        assertEquals(TODAY.toString(), dateOf(f));
    }

    @Test
    void estimateVisit_canBeFinishedWithoutEquipment() throws Exception {
        long job = schedule(oakStreet, TODAY, "ESTIMATE_VISIT");

        complete(job, "{\"equipmentIds\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("DONE"))
                .andExpect(jsonPath("$.serviceRecordIds", hasSize(0)));
    }

    @Test
    void workDone_isRequiredWhenEquipmentIsSelected() throws Exception {
        long job = schedule(oakStreet, TODAY, "TUNE_UP");

        complete(job, "{\"equipmentIds\": [%d], \"workDone\": \" \"}".formatted(outdoorUnit.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("workDone")));
    }

    @Test
    void equipmentAtAnotherAddress_isRejected_andNothingIsSaved() throws Exception {
        long job = schedule(oakStreet, TODAY, "TUNE_UP");

        complete(job, """
                {"equipmentIds": [%d, %d], "workDone": "Tune-up"}
                """.formatted(outdoorUnit.getId(), elmBoiler.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("isn't at this job's address")));

        assertEquals(0, serviceRecords.count());
        mockMvc.perform(get("/api/jobs/" + job).header("Authorization", bearer))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void duplicateEquipmentIds_createOneRecord() throws Exception {
        long job = schedule(oakStreet, TODAY, "TUNE_UP");

        complete(job, """
                {"equipmentIds": [%d, %d], "workDone": "Tune-up"}
                """.formatted(outdoorUnit.getId(), outdoorUnit.getId()))
                .andExpect(jsonPath("$.serviceRecordIds", hasSize(1)));
    }

    @Test
    void startThenFinish_goesThroughInProgress() throws Exception {
        long job = schedule(oakStreet, TODAY, "SERVICE_CALL");

        send(post("/api/jobs/" + job + "/start"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        send(post("/api/jobs/" + job + "/start"), "").andExpect(status().isConflict());
        complete(job, work(outdoorUnit)).andExpect(jsonPath("$.job.status").value("DONE"));
    }

    @Test
    void accidentalStart_canBeUndone() throws Exception {
        long job = schedule(oakStreet, TODAY, "SERVICE_CALL");
        send(post("/api/jobs/" + job + "/start"), "").andExpect(status().isOk());

        send(post("/api/jobs/" + job + "/undo-start"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        // And it can be started again afterwards
        send(post("/api/jobs/" + job + "/start"), "").andExpect(status().isOk());
    }

    @Test
    void undoStart_onlyWorksForJobsInProgress() throws Exception {
        long scheduled = schedule(oakStreet, TODAY, "SERVICE_CALL");
        long done = schedule(oakStreet, TODAY, "TUNE_UP", "15:00");
        complete(done, work(outdoorUnit)).andExpect(status().isOk());

        send(post("/api/jobs/" + scheduled + "/undo-start"), "").andExpect(status().isConflict());
        send(post("/api/jobs/" + done + "/undo-start"), "").andExpect(status().isConflict());
    }

    @Test
    void finishedJob_cantBeFinishedAgain_edited_orCancelled() throws Exception {
        long job = schedule(oakStreet, TODAY, "TUNE_UP");
        complete(job, work(outdoorUnit)).andExpect(status().isOk());

        complete(job, work(outdoorUnit)).andExpect(status().isConflict());
        send(post("/api/jobs/" + job + "/cancel"), "").andExpect(status().isConflict());
        send(put("/api/jobs/" + job), """
                {"propertyId": %d, "date": "%s", "startTime": "15:00", "type": "TUNE_UP"}
                """.formatted(oakStreet, TODAY)).andExpect(status().isConflict());
    }

    @Test
    void cancelledJob_cantBeFinished() throws Exception {
        long job = schedule(oakStreet, TODAY, "TUNE_UP");
        send(post("/api/jobs/" + job + "/cancel"), "").andExpect(status().isOk());

        complete(job, work(outdoorUnit)).andExpect(status().isConflict());
    }

    @Test
    void myDay_showsDoneJobsWithTheirStatus() throws Exception {
        long done = schedule(oakStreet, TODAY, "TUNE_UP");
        schedule(oakStreet, TODAY, "SERVICE_CALL", "15:00");
        complete(done, work(outdoorUnit)).andExpect(status().isOk());

        mockMvc.perform(get("/api/jobs/today").header("Authorization", bearer))
                .andExpect(jsonPath("$.jobs", hasSize(2)))
                .andExpect(jsonPath("$.jobs[0].status").value("DONE"))
                .andExpect(jsonPath("$.jobs[1].status").value("SCHEDULED"));
    }

    @Test
    void complete_unknownJob_returns404() throws Exception {
        complete(999999, work(outdoorUnit)).andExpect(status().isNotFound());
    }

    private long schedule(long propertyId, LocalDate date, String type) throws Exception {
        return schedule(propertyId, date, type, "08:00");
    }

    private long schedule(long propertyId, LocalDate date, String type, String time) throws Exception {
        String body = send(post("/api/jobs"), """
                {"propertyId": %d, "date": "%s", "startTime": "%s", "type": "%s", "allowOverlap": true}
                """.formatted(propertyId, date, time, type))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String work(Equipment unit) {
        return "{\"equipmentIds\": [%d], \"workDone\": \"Did the work\"}".formatted(unit.getId());
    }

    private String kindOf(String completeResponse) throws Exception {
        return recordField(completeResponse, "$.record.kind");
    }

    private String dateOf(String completeResponse) throws Exception {
        return recordField(completeResponse, "$.record.serviceDate");
    }

    private String recordField(String completeResponse, String path) throws Exception {
        long id = ((Number) JsonPath.read(completeResponse, "$.serviceRecordIds[0]")).longValue();
        String record = mockMvc.perform(get("/api/service-records/" + id).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(record, path);
    }

    private ResultActions complete(long jobId, String json) throws Exception {
        return send(post("/api/jobs/" + jobId + "/complete"), json);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
