package dev.servicelog.assistant;

import dev.servicelog.assistant.AssistantDtos.Detail;
import dev.servicelog.assistant.AssistantDtos.Proposal;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.job.JobType;
import dev.servicelog.servicerecord.ServiceKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Things the assistant prepares for the owner to confirm (S34): a job, a customer or a
// service record. Nothing is saved here. Each becomes a card with the real details and
// the exact request the app's normal form would send; the owner taps Confirm (which goes
// through the usual checks, like double-booking) or Edit (which opens the form, filled in).
@Component
public class Proposals {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final CustomerRepository customers;
    private final EquipmentRepository equipment;

    public Proposals(CustomerRepository customers, EquipmentRepository equipment) {
        this.customers = customers;
        this.equipment = equipment;
    }

    static List<Map<String, Object>> definitions() {
        return List.of(
                AssistantTools.tool("propose_job",
                        "Prepare a job for the owner to confirm (nothing is booked until he taps Confirm). "
                                + "Look the customer up first; property_id may be null if they have one address.",
                        Map.of("customer_id", AssistantTools.integer("The customer's id"),
                                "property_id", nullableInteger("The address's property_id; null if only one"),
                                "type", enumOf("TUNE_UP", "SERVICE_CALL", "INSTALL", "ESTIMATE_VISIT"),
                                "date", AssistantTools.dateString(),
                                "start_time", Map.of("type", "string", "description", "24-hour time like 09:00"),
                                "duration_minutes", AssistantTools.integer("15-720; 60 if not said"),
                                "urgent", Map.of("type", "boolean", "description", "No heat, no cooling, leaks"),
                                "notes", AssistantTools.nullableString("What the customer said, access, parts")),
                        List.of("customer_id", "property_id", "type", "date", "start_time", "duration_minutes",
                                "urgent", "notes")),
                AssistantTools.tool("propose_customer",
                        "Prepare a new customer for the owner to confirm (nothing is saved until he taps Confirm). "
                                + "Use null for anything not given; never invent a phone number or ZIP code.",
                        Map.of("name", Map.of("type", "string"),
                                "phone", AssistantTools.nullableString("As given, placeholder included"),
                                "email", AssistantTools.nullableString(""),
                                "street", AssistantTools.nullableString("House number and street"),
                                "unit", AssistantTools.nullableString("Apartment or unit"),
                                "town", AssistantTools.nullableString(""),
                                "state", AssistantTools.nullableString("2 letters; MA if not said"),
                                "zip", AssistantTools.nullableString("Only if given"),
                                "notes", AssistantTools.nullableString("Equipment, access, how they found us")),
                        List.of("name", "phone", "email", "street", "unit", "town", "state", "zip", "notes")),
                AssistantTools.tool("propose_service_record",
                        "Prepare a service record on one piece of equipment for the owner to confirm (nothing is "
                                + "saved until he taps Confirm). Look the equipment up first.",
                        Map.of("equipment_id", AssistantTools.integer("The equipment's id"),
                                "kind", enumOf("TUNE_UP", "REPAIR", "INSTALLATION", "INSPECTION", "OTHER"),
                                "service_date", Map.of("type", List.of("string", "null"),
                                        "description", "A date like 2026-10-22; null for today"),
                                "work_done", Map.of("type", "string"),
                                "parts_used", AssistantTools.nullableString("Only parts actually installed"),
                                "notes", AssistantTools.nullableString("Readings and anything else"),
                                "follow_up_needed", Map.of("type", "boolean"),
                                "follow_up_note", AssistantTools.nullableString("")),
                        List.of("equipment_id", "kind", "service_date", "work_done", "parts_used", "notes",
                                "follow_up_needed", "follow_up_note")));
    }

    static boolean handles(String tool) {
        return tool.startsWith("propose_");
    }

    // Builds the card from real values (args already have placeholders swapped back).
    // Returns what the AI is told: never the details themselves.
    @Transactional(readOnly = true)
    public Map<String, Object> prepare(String tool, JsonNode args, List<Proposal> cards) {
        try {
            Proposal card = switch (tool) {
                case "propose_job" -> job(args);
                case "propose_customer" -> customer(args);
                case "propose_service_record" -> serviceRecord(args);
                default -> throw new IllegalArgumentException("Unknown tool " + tool);
            };
            cards.add(card);
            return Map.of("prepared", true, "missing", card.missing(),
                    "note", "Shown as a card with Confirm and Edit. It is NOT saved until confirmed.");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Proposal job(JsonNode a) {
        Customer c = customers.findWithPropertiesById(a.path("customer_id").asLong())
                .orElseThrow(() -> new IllegalArgumentException("No customer with that id; look them up first"));
        Property property = pickProperty(c, a.path("property_id"));
        JobType type = parseEnum(JobType.class, a.path("type").asString());
        LocalDate date = parseDate(a.path("date").asString());
        LocalTime start = parseTime(a.path("start_time").asString());
        int minutes = Math.clamp(a.path("duration_minutes").asInt(60), 15, 720);
        boolean urgent = a.path("urgent").asBoolean(false);
        String notes = text(a, "notes");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("propertyId", property.getId());
        payload.put("date", date.toString());
        payload.put("startTime", start.toString());
        payload.put("durationMinutes", minutes);
        payload.put("type", type.name());
        payload.put("urgency", urgent ? "URGENT" : "NORMAL");
        payload.put("notes", notes);

        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Customer", c.getName()));
        details.add(new Detail("Address", address(property)));
        details.add(new Detail("When", date.format(DAY) + " at " + start.format(TIME)));
        details.add(new Detail("How long", duration(minutes)));
        if (urgent) {
            details.add(new Detail("Urgent", "Yes"));
        }
        if (notes != null) {
            details.add(new Detail("Notes", notes));
        }
        return new Proposal("JOB", type.getLabel(), details, payload, List.of(), c.getId(), null);
    }

    private Proposal customer(JsonNode a) {
        String name = text(a, "name");
        if (name == null) {
            throw new IllegalArgumentException("A new customer needs a name");
        }
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("id", null);
        property.put("street", text(a, "street"));
        property.put("unit", text(a, "unit"));
        property.put("city", text(a, "town"));
        property.put("state", Optional.ofNullable(text(a, "state")).orElse("MA").toUpperCase(Locale.ROOT));
        property.put("zip", text(a, "zip"));
        property.put("notes", null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("phone", text(a, "phone"));
        payload.put("email", text(a, "email"));
        payload.put("notes", text(a, "notes"));
        payload.put("properties", List.of(property));

        List<String> missing = new ArrayList<>();
        for (String[] required : new String[][] {{"phone", "phone"}, {"street", "street"}, {"town", "town"},
                {"zip", "ZIP code"}}) {
            if (text(a, required[0]) == null) {
                missing.add(required[1]);
            }
        }
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Name", name));
        details.add(new Detail("Phone", Optional.ofNullable(text(a, "phone")).orElse("—")));
        if (text(a, "email") != null) {
            details.add(new Detail("Email", text(a, "email")));
        }
        String street = text(a, "street") == null ? null
                : text(a, "street") + (text(a, "unit") != null ? ", " + text(a, "unit") : "");
        details.add(new Detail("Address", String.join(", ", java.util.stream.Stream.of(street, text(a, "town"),
                ((String) property.get("state")) + (text(a, "zip") != null ? " " + text(a, "zip") : ""))
                .filter(s -> s != null && !s.isBlank()).toList())));
        if (text(a, "notes") != null) {
            details.add(new Detail("Notes", text(a, "notes")));
        }
        return new Proposal("CUSTOMER", "New customer", details, payload, missing, null, null);
    }

    private Proposal serviceRecord(JsonNode a) {
        Equipment unit = equipment.findDetailedById(a.path("equipment_id").asLong())
                .orElseThrow(() -> new IllegalArgumentException("No equipment with that id; look it up first"));
        ServiceKind kind = parseEnum(ServiceKind.class, a.path("kind").asString());
        String dateText = text(a, "service_date");
        LocalDate date = dateText == null ? null : parseDate(dateText);
        String work = text(a, "work_done");
        if (work == null) {
            throw new IllegalArgumentException("A service record needs the work done");
        }
        boolean followUp = a.path("follow_up_needed").asBoolean(false);
        String followUpNote = followUp ? text(a, "follow_up_note") : null;
        // Models like to repeat the follow-up in the notes; keep it in one place
        String notes = text(a, "notes");
        if (notes != null && followUpNote != null && sameText(notes, followUpNote)) {
            notes = null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceDate", date == null ? null : date.toString());
        payload.put("kind", kind.name());
        payload.put("workDone", work);
        payload.put("partsUsed", text(a, "parts_used"));
        payload.put("notes", notes);
        payload.put("followUpNeeded", followUp);
        payload.put("followUpNote", followUpNote);

        Customer c = unit.getProperty().getCustomer();
        String label = unit.getType().getLabel()
                + (unit.getBrand() != null ? " · " + unit.getBrand() : "");
        List<Detail> details = new ArrayList<>();
        details.add(new Detail("Unit", label));
        details.add(new Detail("Customer", c.getName() + " · " + unit.getProperty().getCity()));
        details.add(new Detail("Date", date == null ? "Today" : date.format(DAY)));
        details.add(new Detail("Work done", work));
        if (text(a, "parts_used") != null) {
            details.add(new Detail("Parts", text(a, "parts_used")));
        }
        if (notes != null) {
            details.add(new Detail("Notes", notes));
        }
        if (followUp) {
            details.add(new Detail("Follow-up", Optional.ofNullable(followUpNote).orElse("Yes")));
        }
        return new Proposal("SERVICE_RECORD", kind.getLabel(), details, payload, List.of(), c.getId(),
                unit.getId());
    }

    private static Property pickProperty(Customer c, JsonNode propertyId) {
        if (propertyId.isNumber()) {
            return c.getProperties().stream().filter(p -> p.getId() == propertyId.asLong()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("That property_id isn't one of this customer's"));
        }
        if (c.getProperties().size() == 1) {
            return c.getProperties().get(0);
        }
        throw new IllegalArgumentException("This customer has " + c.getProperties().size()
                + " addresses; ask the owner which one and pass its property_id");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " " + value);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Dates must look like 2026-10-22");
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.length() == 4 ? "0" + value : value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("Times must look like 09:00 (24-hour)");
        }
    }

    private static String address(Property p) {
        return p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity();
    }

    private static String duration(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return (h > 0 ? h + " hr" : "") + (h > 0 && m > 0 ? " " : "") + (m > 0 ? m + " min" : "");
    }

    private static String text(JsonNode args, String field) {
        JsonNode value = args.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString().trim() : null;
    }

    private static boolean sameText(String a, String b) {
        return a.replaceAll("[\\s.]+$", "").equalsIgnoreCase(b.replaceAll("[\\s.]+$", ""));
    }

    private static Map<String, Object> nullableInteger(String description) {
        return Map.of("type", List.of("integer", "null"), "description", description);
    }

    private static Map<String, Object> enumOf(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }
}
