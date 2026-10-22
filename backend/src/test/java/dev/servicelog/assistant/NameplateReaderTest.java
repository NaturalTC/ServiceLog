package dev.servicelog.assistant;

import dev.servicelog.ai.ChatModel;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "Read nameplate" (S36), with a scripted AI
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NameplateReaderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private JavaMailSender mailSender;

    private final List<JsonNode> sentToAi = new ArrayList<>();
    private String bearer;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());
        sentToAi.clear();
    }

    @Test
    void readsThePlate_andSendsACleanSmallImage() throws Exception {
        aiAnswers(Map.of("readable", true, "brand", "Navien", "model", "ncb-240e", "serial_number", "7a1b2c3d",
                "type", "BOILER", "manufactured", "2019-06", "details", List.of("Input 199,900 BTU/h", "120 V"),
                "confidence", "high"));

        read(photo(3000, 2000, "GPSLatitude 42.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readable").value(true))
                .andExpect(jsonPath("$.brand").value("Navien"))
                .andExpect(jsonPath("$.model").value("NCB-240E"))
                .andExpect(jsonPath("$.serialNumber").value("7A1B2C3D"))
                .andExpect(jsonPath("$.type").value("BOILER"))
                .andExpect(jsonPath("$.manufactured").value("2019-06"))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.confidence").value("high"));

        String url = sentToAi.get(0).path("messages").path(1).path("content").path(1).path("image_url").path("url")
                .asString();
        assertThat(url).startsWith("data:image/jpeg;base64,");
        byte[] sent = Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(sent));
        assertThat(Math.max(image.getWidth(), image.getHeight())).isEqualTo(1600);
        assertThat(new String(sent, StandardCharsets.ISO_8859_1)).doesNotContain("Exif", "GPS");
        assertThat(sentToAi.get(0).toString()).contains("\"strict\":true");
    }

    @Test
    void anUnreadablePlate_comesBackEmpty_notGuessed() throws Exception {
        aiAnswers(Map.of("readable", false, "brand", "Carrier", "model", "24ABC6", "serial_number", "1234X",
                "type", "CENTRAL_AC", "manufactured", "2020-01", "details", List.of("R-410A"), "confidence", "high"));

        read(photo(800, 600, null))
                .andExpect(jsonPath("$.readable").value(false))
                .andExpect(jsonPath("$.brand").value(nullValue()))
                .andExpect(jsonPath("$.model").value(nullValue()))
                .andExpect(jsonPath("$.serialNumber").value(nullValue()))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.confidence").value("low"));
    }

    @Test
    void oddValues_areDropped() throws Exception {
        aiAnswers(Map.of("readable", true, "brand", "Trane", "model", "4TTR4036 (maybe)", "serial_number", "?",
                "type", "SPACESHIP", "manufactured", "June 2019", "details", List.of(), "confidence", "medium"));

        read(photo(800, 600, null))
                .andExpect(jsonPath("$.brand").value("Trane"))
                .andExpect(jsonPath("$.model").value(nullValue()))
                .andExpect(jsonPath("$.serialNumber").value(nullValue()))
                .andExpect(jsonPath("$.type").value(nullValue()))
                .andExpect(jsonPath("$.manufactured").value(nullValue()));
    }

    @Test
    void onlyImages_andOnlyLoggedIn() throws Exception {
        read("not an image".getBytes()).andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/assistant/nameplate").file(new MockMultipartFile("photo", "p.jpg",
                "image/jpeg", photo(10, 10, null)))).andExpect(status().isUnauthorized());
    }

    private void aiAnswers(Map<String, Object> fields) {
        String content = json.writeValueAsString(fields);
        when(chatModel.complete(any())).thenAnswer(call -> {
            sentToAi.add(json.valueToTree(call.getArgument(0)));
            return json.readTree(json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                    Map.of("role", "assistant", "content", content))))));
        });
    }

    private ResultActions read(byte[] photo) throws Exception {
        return mockMvc.perform(multipart("/api/assistant/nameplate")
                .file(new MockMultipartFile("photo", "plate.jpg", "image/jpeg", photo))
                .header("Authorization", bearer));
    }

    // A JPEG, optionally with an EXIF segment like a phone camera writes
    private static byte[] photo(int width, int height, String exif) throws Exception {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", plain);
        byte[] bytes = plain.toByteArray();
        if (exif == null) {
            return bytes;
        }
        byte[] payload = ("Exif\0\0" + exif).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes, 0, 2);
        out.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) ((payload.length + 2) >> 8), (byte) (payload.length + 2)});
        out.write(payload);
        out.write(bytes, 2, bytes.length - 2);
        return out.toByteArray();
    }
}
