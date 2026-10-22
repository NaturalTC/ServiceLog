package dev.servicelog.equipment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class EquipmentDtos {

    private EquipmentDtos() {
    }

    // Only the type is required, so a unit can be logged quickly and filled in later
    public record EquipmentRequest(
            @NotNull EquipmentType type,
            @Size(max = 100) String brand,
            @Size(max = 100) String model,
            @Size(max = 100) String serialNumber,
            @PastOrPresent(message = "can't be in the future") LocalDate installDate,
            @Size(max = 2000) String notes) {
    }

    public record EquipmentResponse(Long id, Long propertyId, EquipmentType type, String typeLabel, String brand,
                                    String model, String serialNumber, LocalDate installDate, String notes) {

        static EquipmentResponse from(Equipment e) {
            return new EquipmentResponse(e.getId(), e.getProperty().getId(), e.getType(), e.getType().getLabel(),
                    e.getBrand(), e.getModel(), e.getSerialNumber(), e.getInstallDate(), e.getNotes());
        }
    }

    // The equipment page also shows where it is and whose it is
    public record EquipmentDetailResponse(EquipmentResponse equipment, Long customerId, String customerName,
                                          String address) {
    }

    // For the equipment form's type picker
    public record EquipmentTypeOption(EquipmentType value, String label) {
    }
}
