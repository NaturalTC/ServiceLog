package dev.servicelog.assistant;

import dev.servicelog.ai.AiUnavailableException;
import dev.servicelog.ai.AiUsageLimiter;
import dev.servicelog.ai.ChatModel;
import dev.servicelog.assistant.Pseudonyms.KnownCustomer;
import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.CustomerService;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.job.JobType;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// "Describe it" (S35): one sentence in, a filled-in form out, for the owner to check.
// Nothing is saved here. Same privacy as the assistant: the sentence is scrubbed into
// placeholders before it reaches the AI, and the fields get the real details back.
@Service
public class FormFiller {

    private static final String RULES = """
            You fill in one form in ServiceLog, an app for a one-person HVAC business, from what the owner
            typed or dictated. Use only what the text says; use null for anything it doesn't say. Never
            invent a phone number, ZIP code, serial number, date or time.

            Names, phones, emails and street addresses are replaced with placeholders such as Person_1,
            Customer_14, Phone_1, Email_1, Street_2. Copy placeholders exactly into the matching field
            (a person's name into the name field, Street_2 into street) and never guess the real details.
            Today is %s.
            """;

    private final ChatModel model;
    private final AiUsageLimiter limiter;
    private final AssistantTools tools;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final BusinessCalendar calendar;
    private final JsonMapper json;

    public FormFiller(ChatModel model, AiUsageLimiter limiter, AssistantTools tools, CustomerRepository customers,
                      CustomerService customerService, BusinessCalendar calendar, JsonMapper json) {
        this.model = model;
        this.limiter = limiter;
        this.tools = tools;
        this.customers = customers;
        this.customerService = customerService;
        this.calendar = calendar;
        this.json = json;
    }

    public Map<String, Object> fill(String form, String text) {
        Pseudonyms p = new Pseudonyms();
        List<KnownCustomer> known = tools.knownCustomers();
        String scrubbed = p.scrub(text, known);
        return switch (form) {
            case "customer" -> customer(ask(scrubbed, "customer", CUSTOMER_FORM, CUSTOMER), p);
            case "job" -> job(ask(scrubbed, "job", JOB_FORM, JOB), p);
            case "equipment" -> equipment(ask(scrubbed, "equipment", EQUIPMENT_FORM, EQUIPMENT));
            default -> throw new InvalidRequestException("Unknown form: " + form);
        };
    }

    private JsonNode ask(String text, String name, String instructions, Map<String, Object> schema) {
        LocalDate today = calendar.today();
        String date = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)) + " (" + today + ")";
        limiter.acquire();
        JsonNode message = model.complete(Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", RULES.formatted(date) + "\n" + instructions),
                        Map.of("role", "user", "content", text)),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", name, "strict", true, "schema", schema))))
                .path("choices").path(0).path("message");
        if (message.path("refusal").isString()) {
            throw new AiUnavailableException("The AI couldn't fill this in. Fill in the form by hand.");
        }
        try {
            return json.readTree(message.path("content").asString());
        } catch (RuntimeException e) {
            throw new AiUnavailableException("The AI's answer couldn't be used. Fill in the form by hand.", e);
        }
    }

    // --- Customer ---

    private static final String CUSTOMER_FORM = """
            The form: a new customer. name is the person's name; state is 2 letters (MA when a
            Massachusetts town is given and no state); notes are other useful details (equipment they
            have, access, how they found the business).
            """;

    private static final Map<String, Object> CUSTOMER = object(Map.of(
            "name", nullable("string"), "phone", nullable("string"), "email", nullable("string"),
            "street", nullable("string"), "unit", nullable("string"), "town", nullable("string"),
            "state", nullable("string"), "zip", nullable("string"), "notes", nullable("string")));

    private static Map<String, Object> customer(JsonNode a, Pseudonyms p) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("street", real(a, "street", p));
        property.put("unit", real(a, "unit", p));
        property.put("city", real(a, "town", p));
        property.put("state", Optional.ofNullable(real(a, "state", p)).map(s -> s.toUpperCase(Locale.ROOT))
                .orElse(null));
        property.put("zip", real(a, "zip", p));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", real(a, "name", p));
        out.put("phone", real(a, "phone", p));
        out.put("email", real(a, "email", p));
        out.put("notes", real(a, "notes", p));
        out.put("properties", List.of(property));
        return out;
    }

    // --- Job ---

    private static final String JOB_FORM = """
            The form: scheduling a job. customer is the placeholder or name of who it's for. type:
            TUNE_UP (annual or seasonal service), SERVICE_CALL (a problem: no heat, no cooling, noise,
            leak), INSTALL (new equipment), ESTIMATE_VISIT (pricing a job). date like 2026-10-22 from
            today's date ("next Tuesday", "tomorrow"); start_time 24-hour like 09:00 ("9 in the morning",
            "after lunch" is 13:00); duration_minutes only if said ("an hour and a half" is 90). urgent
            for no heat, no cooling, leaks or anything the customer called urgent. notes: what the
            customer said and anything to bring or know, without repeating the customer, date or time.
            """;

    private static final Map<String, Object> JOB = object(Map.of(
            "customer", nullable("string"),
            "type", Map.of("type", List.of("string", "null"),
                    "enum", Arrays.asList("TUNE_UP", "SERVICE_CALL", "INSTALL", "ESTIMATE_VISIT", null)),
            "date", nullable("string"), "start_time", nullable("string"),
            "duration_minutes", nullable("integer"), "urgent", Map.of("type", "boolean"),
            "notes", nullable("string")));

    // The customer comes back with addresses loaded (see findWithPropertiesById)
    private Map<String, Object> job(JsonNode a, Pseudonyms p) {
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<Customer> customer = findCustomer(text(a, "customer"), p);
        out.put("customerId", customer.map(Customer::getId).orElse(null));
        out.put("customerName", customer.map(Customer::getName).orElse(real(a, "customer", p)));
        out.put("propertyId", customer.filter(c -> c.getProperties().size() == 1)
                .map(c -> c.getProperties().get(0).getId()).orElse(null));
        String type = text(a, "type");
        out.put("type", type != null && Arrays.stream(JobType.values()).anyMatch(t -> t.name().equals(type))
                ? type : null);
        out.put("date", validDate(text(a, "date")));
        out.put("startTime", validTime(text(a, "start_time")));
        out.put("durationMinutes", a.path("duration_minutes").isInt()
                ? Math.clamp(a.path("duration_minutes").asInt(), 15, 720) : null);
        out.put("urgency", a.path("urgent").asBoolean(false) ? "URGENT" : "NORMAL");
        out.put("notes", real(a, "notes", p));
        return out;
    }

    // A Customer placeholder is that customer; a name is looked up, and used only if it's one person
    private Optional<Customer> findCustomer(String value, Pseudonyms p) {
        if (value == null) {
            return Optional.empty();
        }
        Optional<Long> id = p.customerId(value);
        if (id.isPresent()) {
            return customers.findWithPropertiesById(id.get());
        }
        List<Long> matches = customerService.search(p.rehydrate(value)).stream().map(c -> c.id()).toList();
        return matches.size() == 1 ? customers.findWithPropertiesById(matches.get(0)) : Optional.empty();
    }

    // --- Equipment ---

    private static final String EQUIPMENT_FORM = """
            The form: a piece of HVAC equipment at a customer's address. type is one of the listed kinds
            (a "mini-split" with an outdoor condenser is MINI_SPLIT_OUTDOOR, a wall or ceiling head is
            MINI_SPLIT_INDOOR; an oil burner on a boiler is OIL_BURNER). brand as usually written
            (Weil-McLain, Mitsubishi). model and serial_number exactly as given, in capitals.
            install_date like 2026-10-22 (only a year: January 1 of that year). notes: location, filter
            size, quirks.
            """;

    private static final Map<String, Object> EQUIPMENT = object(Map.of(
            "type", Map.of("type", List.of("string", "null"), "enum", equipmentTypes()),
            "brand", nullable("string"), "model", nullable("string"), "serial_number", nullable("string"),
            "install_date", nullable("string"), "notes", nullable("string")));

    private static Map<String, Object> equipment(JsonNode a) {
        Map<String, Object> out = new LinkedHashMap<>();
        String type = text(a, "type");
        out.put("type", type != null && Arrays.stream(EquipmentType.values()).anyMatch(t -> t.name().equals(type))
                ? type : null);
        out.put("brand", text(a, "brand"));
        out.put("model", Optional.ofNullable(text(a, "model")).map(s -> s.toUpperCase(Locale.ROOT)).orElse(null));
        out.put("serialNumber", Optional.ofNullable(text(a, "serial_number"))
                .map(s -> s.toUpperCase(Locale.ROOT)).orElse(null));
        String installed = validDate(text(a, "install_date"));
        // An install date can't be in the future
        out.put("installDate", installed != null && !LocalDate.parse(installed).isAfter(LocalDate.now()) ? installed
                : null);
        out.put("notes", text(a, "notes"));
        return out;
    }

    // --- Helpers ---

    private static List<String> equipmentTypes() {
        List<String> types = new ArrayList<>(Arrays.stream(EquipmentType.values()).map(Enum::name).toList());
        types.add(null);
        return types;
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false, "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    private static Map<String, Object> nullable(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static String text(JsonNode a, String field) {
        JsonNode v = a.path(field);
        return v.isString() && !v.asString().isBlank() ? v.asString().trim() : null;
    }

    // Text with placeholders swapped back for the owner
    private static String real(JsonNode a, String field, Pseudonyms p) {
        String value = text(a, field);
        return value == null ? null : p.rehydrate(value);
    }

    private static String validDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String validTime(String value) {
        try {
            return value == null ? null : LocalTime.parse(value.length() == 4 ? "0" + value : value).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
