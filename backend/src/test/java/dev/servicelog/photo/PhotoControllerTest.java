package dev.servicelog.photo;

import com.jayway.jsonpath.JsonPath;
import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.security.JwtService;
import dev.servicelog.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Photos on service records (S22)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = "servicelog.photos.max-per-record=3")
class PhotoControllerTest {

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

    @Autowired
    private PhotoStorage storage;

    @MockitoBean
    private JavaMailSender mailSender;

    private String bearer;
    private long record;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.reset(jdbc);
        AppUser owner = new AppUser("owner@example.com", "not-used-in-these-tests");
        owner.markEmailVerified();
        users.save(owner);
        bearer = "Bearer " + jwtService.generateToken(owner.getEmail(), owner.getSessionVersion());

        Customer smith = new Customer("Pat Smith", "+14135551234", null, null);
        smith.addProperty(new Property("12 Oak St", null, "Springfield", "MA", "01103", null));
        long property = customers.save(smith).getProperties().get(0).getId();
        long unit = idOf(mockMvc.perform(post("/api/properties/" + property + "/equipment")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"BOILER\"}")).andReturn().getResponse().getContentAsString(), "$.equipment.id");
        record = idOf(mockMvc.perform(post("/api/equipment/" + unit + "/service-records")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\": \"REPAIR\", \"workDone\": \"Replaced the circulator\"}"))
                .andReturn().getResponse().getContentAsString(), "$.record.id");
    }

    @Test
    void upload_storesShrunkJpegsWithThumbnails_inOrder() throws Exception {
        upload(jpeg(3000, 1500, null), png(400, 300))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].width").value(2000)) // shrunk to fit 2000px
                .andExpect(jsonPath("$[0].height").value(1000))
                .andExpect(jsonPath("$[1].width").value(400)) // small images are never enlarged
                .andExpect(jsonPath("$[0].url").value(startsWith("/api/photos/")))
                .andExpect(jsonPath("$[0].thumbUrl").value(containsString("/thumb?exp=")));

        mockMvc.perform(get("/api/service-records/" + record + "/photos").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].height").value(300));
    }

    @Test
    void signedLink_servesTheImage_withoutLogin() throws Exception {
        String body = upload(jpeg(1200, 900, null)).andReturn().getResponse().getContentAsString();
        String full = JsonPath.read(body, "$[0].url");
        String thumb = JsonPath.read(body, "$[0].thumbUrl");

        byte[] image = mockMvc.perform(get(full))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(image)).getWidth()).isEqualTo(1200);

        byte[] small = mockMvc.perform(get(thumb)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(small)).getWidth()).isEqualTo(480);
    }

    @Test
    void imageLinks_withoutAValidSignature_areRefused() throws Exception {
        String body = upload(jpeg(800, 600, null)).andReturn().getResponse().getContentAsString();
        String full = JsonPath.read(body, "$[0].url");
        long id = ((Number) JsonPath.read(body, "$[0].id")).longValue();

        mockMvc.perform(get("/api/photos/" + id + "/full")).andExpect(status().isNotFound());
        mockMvc.perform(get(full.replace("sig=", "sig=x"))).andExpect(status().isNotFound());
        // The thumbnail's signature doesn't open the full image
        mockMvc.perform(get(full.replace("/full?", "/thumb?"))).andExpect(status().isNotFound());
        // An expired link
        mockMvc.perform(get(full.replaceAll("exp=\\d+", "exp=1000"))).andExpect(status().isNotFound());
    }

    @Test
    void upload_stripsHiddenMetadataSuchAsGpsLocation() throws Exception {
        byte[] withExif = jpeg(640, 480, "GPSLatitude 42.1015 GPSLongitude -72.5898");
        assertThat(new String(withExif, StandardCharsets.ISO_8859_1)).contains("Exif", "GPSLatitude");

        String body = upload(withExif).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        byte[] stored = mockMvc.perform(get((String) JsonPath.read(body, "$[0].url")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Exif", "GPSLatitude");
    }

    @Test
    void upload_acceptsOnlyJpegAndPng_andSavesNothingIfOneIsBad() throws Exception {
        MockMultipartFile gif = new MockMultipartFile("files", "a.gif", "image/gif", "GIF89a....".getBytes());
        mockMvc.perform(multipartRequest().file(file(jpeg(100, 100, null))).file(gif).header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG and PNG photos can be added"));

        // A file named .jpg that isn't really an image
        upload("not an image".getBytes()).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/service-records/" + record + "/photos").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void upload_hasAPerRecordLimit() throws Exception {
        upload(jpeg(50, 50, null), jpeg(50, 50, null)).andExpect(status().isCreated());
        upload(jpeg(50, 50, null), jpeg(50, 50, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A service record can have up to 3 photos"));
    }

    @Test
    void delete_removesThePhotoAndItsFiles() throws Exception {
        String body = upload(jpeg(300, 200, null)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$[0].id")).longValue();
        List<String> keys = jdbc.query("select storage_key, thumb_key from service_record_photo where id = ?",
                (rs, n) -> List.of(rs.getString(1), rs.getString(2)), id).get(0);
        assertThat(keys).allMatch(storage::exists);

        mockMvc.perform(delete("/api/photos/" + id).header("Authorization", bearer)).andExpect(status().isNoContent());

        assertThat(keys).noneMatch(storage::exists);
        mockMvc.perform(get("/api/service-records/" + record + "/photos").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get((String) JsonPath.read(body, "$[0].url"))).andExpect(status().isNotFound());
    }

    @Test
    void unknownRecordOrPhoto_returns404() throws Exception {
        mockMvc.perform(multipart("/api/service-records/999999/photos").file(file(jpeg(10, 10, null)))
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/service-records/999999/photos").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/photos/999999").header("Authorization", bearer)).andExpect(status().isNotFound());
    }

    @Test
    void listingUploadingAndDeleting_requireLogin() throws Exception {
        mockMvc.perform(get("/api/service-records/" + record + "/photos")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipartRequest().file(file(jpeg(10, 10, null)))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/photos/1")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions upload(byte[]... images) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipartRequest();
        for (byte[] image : images) {
            request.file(file(image));
        }
        return mockMvc.perform(request.header("Authorization", bearer));
    }

    private MockMultipartHttpServletRequestBuilder multipartRequest() {
        return multipart("/api/service-records/" + record + "/photos");
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("files", "photo.jpg", "image/jpeg", bytes);
    }

    // A JPEG, optionally with an EXIF segment like a phone camera writes (holding the given text)
    private static byte[] jpeg(int width, int height, String exifText) throws IOException {
        byte[] plain = encode(image(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
        if (exifText == null) {
            return plain;
        }
        byte[] payload = ("Exif\0\0" + exifText).getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2); // SOI
        out.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.write(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    private static byte[] png(int width, int height) throws IOException {
        return encode(image(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    private static BufferedImage image(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 80, 20));
        g.fillRect(0, 0, width / 2, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static long idOf(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }
}
