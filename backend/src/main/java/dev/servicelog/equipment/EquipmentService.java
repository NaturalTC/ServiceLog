package dev.servicelog.equipment;

import dev.servicelog.customer.Property;
import dev.servicelog.customer.PropertyRepository;
import dev.servicelog.equipment.EquipmentDtos.EquipmentDetailResponse;
import dev.servicelog.equipment.EquipmentDtos.EquipmentRequest;
import dev.servicelog.equipment.EquipmentDtos.EquipmentResponse;
import dev.servicelog.equipment.EquipmentDtos.UnregisteredUnit;
import dev.servicelog.exception.ConflictException;
import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EquipmentService {

    private final EquipmentRepository equipment;
    private final PropertyRepository properties;

    public EquipmentService(EquipmentRepository equipment, PropertyRepository properties) {
        this.equipment = equipment;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> listForCustomer(long customerId) {
        return equipment.findAllForCustomer(customerId).stream().map(EquipmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UnregisteredUnit> unregisteredInstalls() {
        return equipment.findUnregisteredInstalls().stream()
                .map(e -> new UnregisteredUnit(e.getId(), e.getType().getLabel(), e.getBrand(), e.getModel(),
                        e.getSerialNumber(), e.getInstallDate(), e.getProperty().getCustomer().getId(),
                        e.getProperty().getCustomer().getName(), address(e.getProperty())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentDetailResponse get(long id) {
        return detail(find(id));
    }

    @Transactional
    public EquipmentDetailResponse create(long propertyId, EquipmentRequest request) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        String serial = clean(request.serialNumber());
        ensureSerialIsFree(serial, null);
        checkWarrantyDates(request);

        Equipment unit = new Equipment(property, request.type(), clean(request.brand()), clean(request.model()),
                serial, request.installDate(), clean(request.notes()));
        applyWarranty(unit, request);
        return detail(equipment.save(unit));
    }

    @Transactional
    public EquipmentDetailResponse update(long id, EquipmentRequest request) {
        Equipment existing = find(id);
        String serial = clean(request.serialNumber());
        ensureSerialIsFree(serial, id);
        checkWarrantyDates(request);

        existing.update(request.type(), clean(request.brand()), clean(request.model()), serial,
                request.installDate(), clean(request.notes()));
        applyWarranty(existing, request);
        return detail(equipment.saveAndFlush(existing));
    }

    private static void applyWarranty(Equipment unit, EquipmentRequest request) {
        unit.updateWarranty(request.registered(), request.warrantyRegisteredOn(), request.warrantyEndsOn(),
                request.extendedWarrantyEndsOn());
    }

    // Dates that can't be right, most likely a typo in the year
    private static void checkWarrantyDates(EquipmentRequest request) {
        LocalDate installed = request.installDate();
        if (request.registered() && isBefore(request.warrantyRegisteredOn(), installed)) {
            throw new InvalidRequestException("Warranty registration date can't be before the install date");
        }
        if (isBefore(request.warrantyEndsOn(), installed)) {
            throw new InvalidRequestException("Warranty end date can't be before the install date");
        }
        if (isBefore(request.extendedWarrantyEndsOn(), request.warrantyEndsOn())) {
            throw new InvalidRequestException("Extended warranty can't end before the regular warranty");
        }
    }

    private static boolean isBefore(LocalDate date, LocalDate other) {
        return date != null && other != null && date.isBefore(other);
    }

    // Checked here (not only by the database's unique index) so the owner gets a useful
    // message: where that serial number is already recorded
    private void ensureSerialIsFree(String serial, Long ownId) {
        if (serial == null) {
            return;
        }
        equipment.findFirstBySerialNumberIgnoreCase(serial)
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw new ConflictException("Serial number " + serial + " is already recorded for "
                            + other.getProperty().getCustomer().getName() + " at " + other.getProperty().getStreet());
                });
    }

    private Equipment find(long id) {
        return equipment.findDetailedById(id).orElseThrow(() -> new NotFoundException("Equipment not found"));
    }

    private static EquipmentDetailResponse detail(Equipment e) {
        Property p = e.getProperty();
        return new EquipmentDetailResponse(EquipmentResponse.from(e), p.getCustomer().getId(),
                p.getCustomer().getName(), address(p));
    }

    private static String address(Property p) {
        return p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
    }

    private static String clean(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
