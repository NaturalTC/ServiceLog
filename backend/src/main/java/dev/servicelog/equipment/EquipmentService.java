package dev.servicelog.equipment;

import dev.servicelog.customer.Property;
import dev.servicelog.customer.PropertyRepository;
import dev.servicelog.equipment.EquipmentDtos.EquipmentDetailResponse;
import dev.servicelog.equipment.EquipmentDtos.EquipmentRequest;
import dev.servicelog.equipment.EquipmentDtos.EquipmentResponse;
import dev.servicelog.exception.ConflictException;
import dev.servicelog.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public EquipmentDetailResponse get(long id) {
        return detail(find(id));
    }

    @Transactional
    public EquipmentDetailResponse create(long propertyId, EquipmentRequest request) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        String serial = clean(request.serialNumber());
        ensureSerialIsFree(serial, null);

        Equipment saved = equipment.save(new Equipment(property, request.type(), clean(request.brand()),
                clean(request.model()), serial, request.installDate(), clean(request.notes())));
        return detail(saved);
    }

    @Transactional
    public EquipmentDetailResponse update(long id, EquipmentRequest request) {
        Equipment existing = find(id);
        String serial = clean(request.serialNumber());
        ensureSerialIsFree(serial, id);

        existing.update(request.type(), clean(request.brand()), clean(request.model()), serial,
                request.installDate(), clean(request.notes()));
        return detail(equipment.saveAndFlush(existing));
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
        String address = p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
        return new EquipmentDetailResponse(EquipmentResponse.from(e), p.getCustomer().getId(),
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
