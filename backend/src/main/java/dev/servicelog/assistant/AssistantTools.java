package dev.servicelog.assistant;

import dev.servicelog.assistant.AssistantDtos.CustomerCard;
import dev.servicelog.assistant.Pseudonyms.KnownCustomer;
import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.CustomerRepository;
import dev.servicelog.customer.CustomerService;
import dev.servicelog.customer.Property;
import dev.servicelog.customer.PropertyRepository;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentDtos.ExpiringWarranty;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.equipment.WarrantyService;
import dev.servicelog.job.Job;
import dev.servicelog.job.JobRepository;
import dev.servicelog.servicerecord.ServiceKind;
import dev.servicelog.servicerecord.ServiceRecord;
import dev.servicelog.servicerecord.ServiceRecordRepository;
import dev.servicelog.tuneup.TuneUpDtos.DueUnit;
import dev.servicelog.tuneup.TuneUpService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// What the assistant can look up (S33). Read-only: nothing here changes data.
//
// Every result goes through Pseudonyms: customers' names, phones, emails and streets
// become placeholders, and free text (notes, work done) is scrubbed the same way. Ids,
// towns, equipment, dates and work details are not personal and are passed as they are.
@Component
public class AssistantTools {

    private static final int MAX_RESULTS = 8;
    private static final int MAX_DAYS = 31;

    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final PropertyRepository properties;
    private final EquipmentRepository equipment;
    private final ServiceRecordRepository records;
    private final JobRepository jobs;
    private final TuneUpService tuneUps;
    private final WarrantyService warranties;
    private final BusinessCalendar calendar;

    public AssistantTools(CustomerRepository customers, CustomerService customerService, PropertyRepository properties,
                          EquipmentRepository equipment, ServiceRecordRepository records, JobRepository jobs,
                          TuneUpService tuneUps, WarrantyService warranties, BusinessCalendar calendar) {
        this.customers = customers;
        this.customerService = customerService;
        this.properties = properties;
        this.equipment = equipment;
        this.records = records;
        this.jobs = jobs;
        this.tuneUps = tuneUps;
        this.warranties = warranties;
        this.calendar = calendar;
    }

    // Who the owner might mention by name, for scrubbing what he types
    @Transactional(readOnly = true)
    public List<KnownCustomer> knownCustomers() {
        return customers.findAllByOrderByNameAscIdAsc().stream()
                .map(c -> new KnownCustomer(c.getId(), c.getName(), c.getPhone(), c.getEmail(),
                        c.getProperties().stream().map(Property::getStreet).toList()))
                .toList();
    }

    // Cards for the customers an answer mentions (real details: these go to the owner, not the AI)
    @Transactional(readOnly = true)
    public List<CustomerCard> cards(List<Long> ids) {
        return ids.stream()
                .map(customers::findWithPropertiesById)
                .flatMap(Optional::stream)
                .map(c -> {
                    String address = c.getProperties().isEmpty() ? null : formatted(c.getProperties().get(0));
                    return new CustomerCard(c.getId(), c.getName(),
                            c.getPhone(), address);
                })
                .toList();
    }

    private static String formatted(Property p) {
        return p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
    }

    // The tools, in OpenAI's function-calling format (strict schemas)
    static List<Map<String, Object>> definitions() {
        return List.of(
                tool("find_customers",
                        "Find customers by name, street, phone or placeholder (e.g. Customer_2), and/or by town. "
                                + "Returns their addresses and equipment with ids.",
                        Map.of("query", nullableString("Name, street, phone or placeholder; null to list by town"),
                                "town", nullableString("Only customers with an address in this town")),
                        List.of("query", "town")),
                tool("get_customer",
                        "One customer in full: addresses, equipment with last service and last tune-up, "
                                + "upcoming jobs, notes.",
                        Map.of("customer_id", integer("The customer's id")), List.of("customer_id")),
                tool("get_service_history",
                        "The service history of one piece of equipment, newest first.",
                        Map.of("equipment_id", integer("The equipment's id"),
                                "limit", integer("How many visits, 1-20")),
                        List.of("equipment_id", "limit")),
                tool("list_jobs",
                        "Scheduled jobs between two dates (inclusive, at most 31 days), in time order.",
                        Map.of("from", dateString(), "to", dateString()), List.of("from", "to")),
                tool("tune_ups_due",
                        "Equipment due for a seasonal tune-up today, by address, with whether one is booked.",
                        Map.of("town", nullableString("Only this town; null for all")), List.of("town")),
                tool("warranties_ending",
                        "Equipment whose warranty or extended warranty ends in the next 90 days.",
                        Map.of("town", nullableString("Only this town; null for all")), List.of("town")),
                tool("troubleshooting_context",
                        "For a symptom on one unit: its model, install date and full service history, plus "
                                + "repairs on the owner's other units of the same type (same_model marks the same "
                                + "brand and model). Use before suggesting causes.",
                        Map.of("equipment_id", integer("The equipment's id")), List.of("equipment_id")),
                tool("open_follow_ups",
                        "Follow-ups noted on service visits in the last 12 months (quotes to send, parts on "
                                + "order, return visits), newest first.",
                        Map.of(), List.of()));
    }

    // Runs one tool; args are already turned back into real values. Returns a JSON-ready result.
    // Each tool call is its own short transaction: no database connection is held while
    // waiting for the AI.
    @Transactional(readOnly = true)
    public Object run(String name, JsonNode args, Pseudonyms p, List<KnownCustomer> known) {
        return switch (name) {
            case "find_customers" -> findCustomers(text(args, "query"), text(args, "town"), p, known);
            case "get_customer" -> getCustomer(args.path("customer_id").asLong(), p, known);
            case "get_service_history" -> serviceHistory(args.path("equipment_id").asLong(),
                    Math.clamp(args.path("limit").asInt(5), 1, 20), p, known);
            case "list_jobs" -> listJobs(text(args, "from"), text(args, "to"), p, known);
            case "tune_ups_due" -> tuneUpsDue(text(args, "town"), p);
            case "warranties_ending" -> warrantiesEnding(text(args, "town"), p);
            case "open_follow_ups" -> openFollowUps(p, known);
            case "troubleshooting_context" -> troubleshooting(args.path("equipment_id").asLong(), p, known);
            default -> Map.of("error", "Unknown tool " + name);
        };
    }

    private Object findCustomers(String query, String town, Pseudonyms p, List<KnownCustomer> known) {
        List<Customer> found;
        Optional<Long> byPlaceholder = p.customerId(query);
        if (byPlaceholder.isPresent()) {
            found = customers.findWithPropertiesById(byPlaceholder.get()).stream().toList();
        } else if (query != null && !query.isBlank()) {
            List<Long> ids = customerService.search(query).stream().map(c -> c.id()).toList();
            found = customers.findAllById(ids).stream().toList();
        } else {
            found = customers.findAllByOrderByNameAscIdAsc();
        }
        List<Customer> matching = found.stream()
                .filter(c -> town == null || town.isBlank() || c.getProperties().stream()
                        .anyMatch(pr -> pr.getCity().equalsIgnoreCase(town.trim())))
                .toList();
        List<Map<String, Object>> results = matching.stream().limit(MAX_RESULTS).map(c -> summary(c, p, known)).toList();
        return Map.of("count", matching.size(), "customers", results,
                "note", matching.size() > MAX_RESULTS ? "Only the first " + MAX_RESULTS + " shown" : "");
    }

    private Map<String, Object> summary(Customer c, Pseudonyms p, List<KnownCustomer> known) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customer", p.customer(c.getId(), c.getName()));
        out.put("customer_id", c.getId());
        out.put("phone", p.phone(c.getPhone()));
        out.put("email", p.email(c.getEmail()));
        out.put("addresses", c.getProperties().stream().map(pr -> address(pr, p)).toList());
        out.put("equipment", equipment.findAllForCustomer(c.getId()).stream().map(this::unit).toList());
        return out;
    }

    private Object getCustomer(long id, Pseudonyms p, List<KnownCustomer> known) {
        Optional<Customer> customer = customers.findWithPropertiesById(id);
        if (customer.isEmpty()) {
            return Map.of("error", "No customer with id " + id);
        }
        Customer c = customer.get();
        Map<String, Object> out = summary(c, p, known);
        out.put("notes", p.scrub(c.getNotes(), known));
        out.put("reminder_emails", c.getRemindersOptedOutAt() != null ? "unsubscribed" : "allowed");
        out.put("equipment", equipment.findAllForCustomer(id).stream().map(e -> {
            Map<String, Object> unit = unit(e);
            List<ServiceRecord> history = records.findByEquipmentIdOrderByServiceDateDescIdDesc(e.getId());
            unit.put("last_service", history.isEmpty() ? null : history.get(0).getServiceDate());
            unit.put("last_tune_up", history.stream().filter(r -> r.getKind() == ServiceKind.TUNE_UP)
                    .map(ServiceRecord::getServiceDate).findFirst().orElse(null));
            unit.put("visits_on_record", history.size());
            return unit;
        }).toList());
        out.put("upcoming_jobs", jobs.findUpcomingForCustomer(id, calendar.today()).stream()
                .map(j -> job(j, p, known)).toList());
        return out;
    }

    private Object serviceHistory(long equipmentId, int limit, Pseudonyms p, List<KnownCustomer> known) {
        Optional<Equipment> found = equipment.findDetailedById(equipmentId);
        if (found.isEmpty()) {
            return Map.of("error", "No equipment with id " + equipmentId);
        }
        Equipment e = found.get();
        Customer c = e.getProperty().getCustomer();
        Map<String, Object> out = unit(e);
        out.put("customer", p.customer(c.getId(), c.getName()));
        out.put("customer_id", c.getId());
        out.put("address", address(e.getProperty(), p));
        out.put("notes", p.scrub(e.getNotes(), known));
        List<ServiceRecord> history = records.findByEquipmentIdOrderByServiceDateDescIdDesc(equipmentId);
        out.put("visits_on_record", history.size());
        out.put("visits", history.stream().limit(limit).map(r -> {
            Map<String, Object> visit = new LinkedHashMap<>();
            visit.put("date", r.getServiceDate());
            visit.put("kind", r.getKind().getLabel());
            visit.put("work_done", p.scrub(r.getWorkDone(), known));
            visit.put("parts_used", p.scrub(r.getPartsUsed(), known));
            visit.put("notes", p.scrub(r.getNotes(), known));
            visit.put("follow_up", r.isFollowUpNeeded()
                    ? Objects.requireNonNullElse(p.scrub(r.getFollowUpNote(), known), "yes") : null);
            return visit;
        }).toList());
        return out;
    }

    private Object listJobs(String fromText, String toText, Pseudonyms p, List<KnownCustomer> known) {
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(fromText);
            to = LocalDate.parse(toText);
        } catch (DateTimeParseException | NullPointerException e) {
            return Map.of("error", "Dates must look like 2026-10-22");
        }
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            return Map.of("error", "Ask for at most " + MAX_DAYS + " days, with 'to' on or after 'from'");
        }
        return Map.of("jobs", jobs.findScheduledBetween(from, to).stream().map(j -> job(j, p, known)).toList());
    }

    private Map<String, Object> job(Job j, Pseudonyms p, List<KnownCustomer> known) {
        Property pr = j.getProperty();
        Customer c = pr.getCustomer();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job_id", j.getId());
        out.put("date", j.getScheduledDate());
        out.put("weekday", j.getScheduledDate().getDayOfWeek().toString().charAt(0)
                + j.getScheduledDate().getDayOfWeek().toString().substring(1).toLowerCase(Locale.ROOT));
        out.put("start", j.getStartTime());
        out.put("minutes", j.getDurationMinutes());
        out.put("type", j.getType().getLabel());
        out.put("status", j.getStatus());
        out.put("urgent", j.getUrgency().name().equals("URGENT"));
        out.put("customer", p.customer(c.getId(), c.getName()));
        out.put("customer_id", c.getId());
        out.put("address", address(pr, p));
        out.put("notes", p.scrub(j.getNotes(), known));
        return out;
    }

    private Object tuneUpsDue(String town, Pseudonyms p) {
        List<DueUnit> due = tuneUps.due().units();
        Map<Long, Property> byId = properties.findAllById(due.stream().map(DueUnit::propertyId).distinct().toList())
                .stream().collect(Collectors.toMap(Property::getId, Function.identity()));
        return Map.of("units", due.stream().filter(u -> inTown(town, byId.containsKey(u.propertyId())
                ? byId.get(u.propertyId()).getCity() : null)).map(u -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("customer", p.customer(u.customerId(), u.customerName()));
            out.put("customer_id", u.customerId());
            out.put("address", byId.containsKey(u.propertyId()) ? address(byId.get(u.propertyId()), p) : null);
            out.put("equipment_id", u.equipmentId());
            out.put("equipment", u.equipmentLabel() + brandModel(u.brand(), u.model()));
            out.put("last_tune_up", u.lastTuneUp());
            out.put("booked_for", u.bookedDate());
            out.put("reminder_emailed", u.lastReminderAt() != null);
            out.put("emails_allowed", u.customerEmail() != null && !u.remindersOptedOut());
            return out;
        }).toList());
    }

    private Object warrantiesEnding(String town, Pseudonyms p) {
        List<ExpiringWarranty> ending = warranties.endingSoon().units();
        return Map.of("units", ending.stream().filter(w -> inTown(town, townFrom(w.address()))).map(w -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("customer", p.customer(w.customerId(), w.customerName()));
            out.put("customer_id", w.customerId());
            out.put("town", townFrom(w.address()));
            out.put("equipment_id", w.equipmentId());
            out.put("equipment", w.typeLabel() + brandModel(w.brand(), w.model()));
            out.put("ends_on", w.endsOn());
            out.put("days_left", w.daysLeft());
            out.put("offer", w.offer() == dev.servicelog.equipment.EquipmentDtos.Offer.RENEWAL
                    ? "renew the extended warranty" : "an extended warranty");
            out.put("already_offered_on", w.offeredOn());
            return out;
        }).toList());
    }

    private Object troubleshooting(long equipmentId, Pseudonyms p, List<KnownCustomer> known) {
        Object history = serviceHistory(equipmentId, 15, p, known);
        Optional<Equipment> found = equipment.findDetailedById(equipmentId);
        if (found.isEmpty()) {
            return history; // the "no equipment" error
        }
        Equipment unit = found.get();
        List<Map<String, Object>> others = records
                .findRepairsOnOtherUnitsOfType(unit.getType(), equipmentId, PageRequest.of(0, 25)).stream()
                .map(r -> {
                    Equipment e = r.getEquipment();
                    Map<String, Object> repair = new LinkedHashMap<>();
                    repair.put("date", r.getServiceDate());
                    repair.put("same_model", Objects.equals(lower(e.getBrand()), lower(unit.getBrand()))
                            && Objects.equals(lower(e.getModel()), lower(unit.getModel())) && e.getModel() != null);
                    repair.put("equipment", e.getType().getLabel() + brandModel(e.getBrand(), e.getModel()));
                    repair.put("installed", e.getInstallDate());
                    repair.put("work_done", p.scrub(r.getWorkDone(), known));
                    repair.put("parts_used", p.scrub(r.getPartsUsed(), known));
                    return repair;
                }).toList();
        return Map.of("unit", history, "repairs_on_other_units_of_this_type", others);
    }

    private static String lower(String s) {
        return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private Object openFollowUps(Pseudonyms p, List<KnownCustomer> known) {
        LocalDate since = calendar.today().minusMonths(12);
        return Map.of("follow_ups", records
                .findTop30ByFollowUpNeededTrueAndServiceDateGreaterThanEqualOrderByServiceDateDescIdDesc(since)
                .stream().map(r -> {
                    Equipment e = r.getEquipment();
                    Customer c = e.getProperty().getCustomer();
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("date", r.getServiceDate());
                    out.put("customer", p.customer(c.getId(), c.getName()));
                    out.put("customer_id", c.getId());
                    out.put("town", e.getProperty().getCity());
                    out.put("equipment_id", e.getId());
                    out.put("equipment", e.getType().getLabel() + brandModel(e.getBrand(), e.getModel()));
                    out.put("follow_up", p.scrub(r.getFollowUpNote(), known));
                    out.put("work_done", p.scrub(r.getWorkDone(), known));
                    return out;
                }).toList());
    }

    private Map<String, Object> unit(Equipment e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipment_id", e.getId());
        out.put("property_id", e.getProperty().getId());
        out.put("type", e.getType().getLabel());
        out.put("brand", e.getBrand());
        out.put("model", e.getModel());
        out.put("serial", e.getSerialNumber());
        out.put("installed", e.getInstallDate());
        out.put("warranty_ends", e.getCoverEndsOn());
        return out;
    }

    private static Map<String, Object> address(Property pr, Pseudonyms p) {
        String street = pr.getStreet() + (pr.getUnit() != null ? ", " + pr.getUnit() : "");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("property_id", pr.getId());
        out.put("street", p.street(street));
        out.put("town", pr.getCity());
        return out;
    }

    private static boolean inTown(String wanted, String town) {
        return wanted == null || wanted.isBlank() || wanted.trim().equalsIgnoreCase(town);
    }

    // "12 Oak St, Springfield, MA 01103" -> "Springfield"
    private static String townFrom(String address) {
        String[] parts = address.split(",\\s*");
        return parts.length >= 2 ? parts[parts.length - 2] : null;
    }

    private static String brandModel(String brand, String model) {
        String bm = java.util.stream.Stream.of(brand, model).filter(Objects::nonNull).collect(Collectors.joining(" "));
        return bm.isBlank() ? "" : " (" + bm + ")";
    }

    private static String text(JsonNode args, String field) {
        JsonNode value = args.path(field);
        return value.isString() ? value.asString() : null;
    }

    static Map<String, Object> tool(String name, String description, Map<String, Object> properties,
                                            List<String> required) {
        return Map.of("type", "function", "function", Map.of(
                "name", name,
                "description", description,
                "strict", true,
                "parameters", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", properties,
                        "required", required)));
    }

    static Map<String, Object> nullableString(String description) {
        return Map.of("type", List.of("string", "null"), "description", description);
    }

    static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    static Map<String, Object> dateString() {
        return Map.of("type", "string", "description", "A date like 2026-10-22");
    }
}
