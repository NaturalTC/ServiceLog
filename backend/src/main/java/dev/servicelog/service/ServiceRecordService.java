package dev.servicelog.service;

import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.customer.Property;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.exception.NotFoundException;
import dev.servicelog.service.ServiceRecordDtos.ServiceRecordDetailResponse;
import dev.servicelog.service.ServiceRecordDtos.ServiceRecordRequest;
import dev.servicelog.service.ServiceRecordDtos.ServiceRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ServiceRecordService {

    private final ServiceRecordRepository records;
    private final EquipmentRepository equipment;
    private final BusinessCalendar calendar;

    public ServiceRecordService(ServiceRecordRepository records, EquipmentRepository equipment,
                                BusinessCalendar calendar) {
        this.records = records;
        this.equipment = equipment;
        this.calendar = calendar;
    }

    @Transactional
    public ServiceRecordDetailResponse create(long equipmentId, ServiceRecordRequest request) {
        Equipment unit = equipment.findDetailedById(equipmentId)
                .orElseThrow(() -> new NotFoundException("Equipment not found"));
        ServiceRecord saved = records.save(new ServiceRecord(unit, dateOrToday(request.serviceDate()),
                request.kind(), request.workDone().trim(), clean(request.partsUsed()), clean(request.notes()),
                request.needsFollowUp(), clean(request.followUpNote())));
        return detail(saved);
    }

    @Transactional(readOnly = true)
    public ServiceRecordDetailResponse get(long id) {
        return detail(find(id));
    }

    @Transactional
    public ServiceRecordDetailResponse update(long id, ServiceRecordRequest request) {
        ServiceRecord record = find(id);
        record.update(dateOrToday(request.serviceDate()), request.kind(), request.workDone().trim(),
                clean(request.partsUsed()), clean(request.notes()), request.needsFollowUp(),
                clean(request.followUpNote()));
        return detail(records.saveAndFlush(record));
    }

    // Defaults to today in the business's time zone, not the server's
    private LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : calendar.today();
    }

    private ServiceRecord find(long id) {
        return records.findDetailedById(id).orElseThrow(() -> new NotFoundException("Service record not found"));
    }

    private static ServiceRecordDetailResponse detail(ServiceRecord r) {
        Equipment unit = r.getEquipment();
        Property p = unit.getProperty();
        String label = unit.getType().getLabel() + (unit.getBrand() != null ? " · " + unit.getBrand() : "");
        String address = p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
        return new ServiceRecordDetailResponse(ServiceRecordResponse.from(r), label, p.getCustomer().getId(),
                p.getCustomer().getName(), address);
    }

    private static String clean(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
