package dev.servicelog.equipment;

import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.customer.Customer;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.EquipmentDtos.ExpiringWarranties;
import dev.servicelog.equipment.EquipmentDtos.ExpiringWarranty;
import dev.servicelog.equipment.EquipmentDtos.Offer;
import dev.servicelog.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

// Warranties ending soon (S25): a chance to offer an extended warranty, or to renew one.
//
// What counts is the last day of cover. A unit whose regular warranty ends next month but
// whose extended warranty runs for years isn't ending soon.
@Service
public class WarrantyService {

    private final EquipmentRepository equipment;
    private final BusinessCalendar calendar;
    private final int windowDays;

    public WarrantyService(EquipmentRepository equipment, BusinessCalendar calendar,
                           @Value("${servicelog.warranty.reminder-days}") int windowDays) {
        this.equipment = equipment;
        this.calendar = calendar;
        this.windowDays = windowDays;
    }

    // Not offered yet first, then soonest ending
    @Transactional(readOnly = true)
    public ExpiringWarranties endingSoon() {
        LocalDate today = calendar.today();
        LocalDate until = today.plusDays(windowDays);
        List<ExpiringWarranty> units = equipment.findWithWarrantyEndingBetween(today, until).stream()
                .filter(e -> !e.getCoverEndsOn().isBefore(today) && !e.getCoverEndsOn().isAfter(until))
                .sorted(Comparator.comparing((Equipment e) -> e.getWarrantyOfferMadeOn() != null)
                        .thenComparing(Equipment::getCoverEndsOn)
                        .thenComparing(Equipment::getId))
                .map(e -> toExpiring(e, today))
                .toList();
        return new ExpiringWarranties(windowDays, units);
    }

    @Transactional
    public void markOffered(long id, boolean offered) {
        Equipment unit = equipment.findById(id).orElseThrow(() -> new NotFoundException("Equipment not found"));
        unit.markWarrantyOffered(offered ? calendar.today() : null);
    }

    private static ExpiringWarranty toExpiring(Equipment e, LocalDate today) {
        LocalDate endsOn = e.getCoverEndsOn();
        Offer offer = endsOn.equals(e.getExtendedWarrantyEndsOn()) ? Offer.RENEWAL : Offer.EXTENDED_WARRANTY;
        Property p = e.getProperty();
        Customer c = p.getCustomer();
        String address = p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
        return new ExpiringWarranty(e.getId(), e.getType().getLabel(), e.getBrand(), e.getModel(), e.getSerialNumber(),
                offer, endsOn, ChronoUnit.DAYS.between(today, endsOn), e.getWarrantyOfferMadeOn(), c.getId(),
                c.getName(), c.getPhone(), c.getEmail(), address);
    }
}
