package dev.servicelog.tuneup;

import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.job.Job;
import dev.servicelog.tuneup.TuneUpDtos.DueResponse;
import dev.servicelog.tuneup.TuneUpDtos.DueUnit;
import dev.servicelog.tuneup.TuneUpDtos.Season;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Which equipment is due for a seasonal tune-up today (S10).
//
// A unit is due when it's in season and its last tune-up (or installation, for a new unit)
// was at least `intervalMonths` ago, or there's none on record. Repairs don't count: a
// January repair isn't the annual tune-up.
@Service
public class TuneUpService {

    private final TuneUpQueries queries;
    private final TuneUpRules rules;
    private final BusinessCalendar calendar;
    private final TuneUpReminderRepository reminders;

    public TuneUpService(TuneUpQueries queries, TuneUpRules rules, BusinessCalendar calendar,
                         TuneUpReminderRepository reminders) {
        this.queries = queries;
        this.rules = rules;
        this.calendar = calendar;
        this.reminders = reminders;
    }

    @Transactional(readOnly = true)
    public DueResponse due() {
        return due(calendar.today());
    }

    @Transactional(readOnly = true)
    public DueResponse due(LocalDate today) {
        boolean heating = rules.isHeatingSeason(today);
        boolean cooling = rules.isCoolingSeason(today);

        List<EquipmentType> inSeason = Arrays.stream(EquipmentType.values())
                .filter(type -> seasonFor(type, heating, cooling) != null)
                .toList();
        if (inSeason.isEmpty()) {
            return new DueResponse(today, false, false, rules.intervalMonths(), List.of());
        }

        // First booked tune-up per address
        Map<Long, Job> bookedByProperty = new HashMap<>();
        for (Job job : queries.openTuneUpsFrom(today)) {
            bookedByProperty.putIfAbsent(job.getProperty().getId(), job);
        }

        // Reminders sent this season (within the interval), so the list can say "Reminder sent"
        Map<Long, Instant> remindedByProperty = new HashMap<>();
        Instant since = today.minusMonths(rules.intervalMonths()).atStartOfDay(ZoneOffset.UTC).toInstant();
        for (Object[] row : reminders.latestSentPerPropertySince(since)) {
            remindedByProperty.put((Long) row[0], (Instant) row[1]);
        }

        LocalDate dueIfOnOrBefore = today.minusMonths(rules.intervalMonths());
        List<DueUnit> units = queries.seasonalEquipmentWithLastTuneUp(inSeason).stream()
                .filter(row -> {
                    LocalDate last = baseline(row);
                    return last == null || !last.isAfter(dueIfOnOrBefore);
                })
                .map(row -> toDueUnit(row, seasonFor(TuneUpQueries.equipment(row).getType(), heating, cooling),
                        bookedByProperty, remindedByProperty))
                .sorted(Comparator
                        // Not yet booked first: those are the ones to call
                        .comparing((DueUnit u) -> u.bookedJobId() != null)
                        // Longest overdue first; never tuned up counts as the most overdue
                        .thenComparing(DueUnit::lastTuneUp, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(DueUnit::customerName)
                        .thenComparing(DueUnit::equipmentId))
                .toList();

        return new DueResponse(today, heating, cooling, rules.intervalMonths(), units);
    }

    // Heating equipment in heating season, cooling in cooling season; heat pumps and
    // mini-splits do both, so they can come due in either
    static Season seasonFor(EquipmentType type, boolean heating, boolean cooling) {
        return switch (type.getSeason()) {
            case HEATING -> heating ? Season.HEATING : null;
            case COOLING -> cooling ? Season.COOLING : null;
            case BOTH -> heating ? Season.HEATING : cooling ? Season.COOLING : null;
            case NONE -> null;
        };
    }

    // Last tune-up or install record; failing that, the install date on the unit itself
    private static LocalDate baseline(Object[] row) {
        LocalDate fromRecords = TuneUpQueries.lastTuneUp(row);
        LocalDate installed = TuneUpQueries.equipment(row).getInstallDate();
        if (fromRecords == null) {
            return installed;
        }
        return installed == null || fromRecords.isAfter(installed) ? fromRecords : installed;
    }

    private static DueUnit toDueUnit(Object[] row, Season season, Map<Long, Job> bookedByProperty,
                                     Map<Long, Instant> remindedByProperty) {
        Equipment e = TuneUpQueries.equipment(row);
        Property p = e.getProperty();
        Customer c = p.getCustomer();
        Job booked = bookedByProperty.get(p.getId());
        String address = p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
        return new DueUnit(e.getId(), e.getType().getLabel(), e.getBrand(), e.getModel(), season, baseline(row),
                c.getId(), c.getName(), c.getPhone(), c.getEmail(), p.getId(), address,
                booked == null ? null : booked.getId(),
                booked == null ? null : booked.getScheduledDate(),
                remindedByProperty.get(p.getId()),
                c.getRemindersOptedOutAt() != null);
    }
}
