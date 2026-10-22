package dev.servicelog.equipment;

import dev.servicelog.equipment.EquipmentDtos.EquipmentDetailResponse;
import dev.servicelog.equipment.EquipmentDtos.EquipmentRequest;
import dev.servicelog.equipment.EquipmentDtos.EquipmentResponse;
import dev.servicelog.equipment.EquipmentDtos.EquipmentTypeOption;
import dev.servicelog.equipment.EquipmentDtos.ExpiringWarranties;
import dev.servicelog.equipment.EquipmentDtos.UnregisteredUnit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

// All routes require login (see SecurityConfig)
@RestController
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final WarrantyService warrantyService;

    public EquipmentController(EquipmentService equipmentService, WarrantyService warrantyService) {
        this.equipmentService = equipmentService;
        this.warrantyService = warrantyService;
    }

    @GetMapping("/api/equipment-types")
    public List<EquipmentTypeOption> types() {
        return Arrays.stream(EquipmentType.values())
                .map(type -> new EquipmentTypeOption(type, type.getLabel()))
                .toList();
    }

    // Everything installed at any of the customer's properties, for the customer page
    @GetMapping("/api/customers/{customerId}/equipment")
    public List<EquipmentResponse> listForCustomer(@PathVariable long customerId) {
        return equipmentService.listForCustomer(customerId);
    }

    @PostMapping("/api/properties/{propertyId}/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentDetailResponse create(@PathVariable long propertyId, @Valid @RequestBody EquipmentRequest request) {
        return equipmentService.create(propertyId, request);
    }

    // Installed units whose warranty still has to be registered with the manufacturer
    @GetMapping("/api/equipment/unregistered")
    public List<UnregisteredUnit> unregistered() {
        return equipmentService.unregisteredInstalls();
    }

    // Units whose warranty cover ends soon, to offer an extended warranty or a renewal
    @GetMapping("/api/equipment/warranty-ending")
    public ExpiringWarranties warrantyEnding() {
        return warrantyService.endingSoon();
    }

    // "I've offered it": moves the unit down the list (DELETE undoes it)
    @PutMapping("/api/equipment/{id}/warranty-offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markWarrantyOffered(@PathVariable long id) {
        warrantyService.markOffered(id, true);
    }

    @DeleteMapping("/api/equipment/{id}/warranty-offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmarkWarrantyOffered(@PathVariable long id) {
        warrantyService.markOffered(id, false);
    }

    @GetMapping("/api/equipment/{id}")
    public EquipmentDetailResponse get(@PathVariable long id) {
        return equipmentService.get(id);
    }

    @PutMapping("/api/equipment/{id}")
    public EquipmentDetailResponse update(@PathVariable long id, @Valid @RequestBody EquipmentRequest request) {
        return equipmentService.update(id, request);
    }
}
