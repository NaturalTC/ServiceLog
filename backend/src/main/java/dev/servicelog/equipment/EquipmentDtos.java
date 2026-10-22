package dev.servicelog.equipment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

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
            @Size(max = 2000) String notes,
            // Warranty (all optional; a missing "registered" means not registered)
            Boolean warrantyRegistered,
            @PastOrPresent(message = "can't be in the future") LocalDate warrantyRegisteredOn,
            LocalDate warrantyEndsOn,
            LocalDate extendedWarrantyEndsOn) {

        boolean registered() {
            return Boolean.TRUE.equals(warrantyRegistered);
        }
    }

    public record EquipmentResponse(Long id, Long propertyId, EquipmentType type, String typeLabel, String brand,
                                    String model, String serialNumber, LocalDate installDate, String notes,
                                    boolean warrantyRegistered, LocalDate warrantyRegisteredOn,
                                    LocalDate warrantyEndsOn, LocalDate extendedWarrantyEndsOn) {

        static EquipmentResponse from(Equipment e) {
            return new EquipmentResponse(e.getId(), e.getProperty().getId(), e.getType(), e.getType().getLabel(),
                    e.getBrand(), e.getModel(), e.getSerialNumber(), e.getInstallDate(), e.getNotes(),
                    e.isWarrantyRegistered(), e.getWarrantyRegisteredOn(), e.getWarrantyEndsOn(),
                    e.getExtendedWarrantyEndsOn());
        }
    }

    // An installed unit whose warranty isn't registered yet, with what's needed to register it
    public record UnregisteredUnit(Long equipmentId, String typeLabel, String brand, String model,
                                   String serialNumber, LocalDate installDate, Long customerId,
                                   String customerName, String address) {
    }

    // The equipment page also shows where it is and whose it is
    public record EquipmentDetailResponse(EquipmentResponse equipment, Long customerId, String customerName,
                                          String address) {
    }

    // For the equipment form's type picker
    public record EquipmentTypeOption(EquipmentType value, String label) {
    }

    // A unit whose cover ends soon: an opportunity to offer an extended warranty or a renewal
    public record ExpiringWarranty(Long equipmentId, String typeLabel, String brand, String model,
                                   String serialNumber, Offer offer, LocalDate endsOn, long daysLeft,
                                   LocalDate offeredOn, Long customerId, String customerName,
                                   String customerPhone, String customerEmail, String address) {
    }

    public enum Offer {
        // Only the regular warranty: offer an extended one
        EXTENDED_WARRANTY,
        // The extended warranty is the one ending: offer to renew it
        RENEWAL
    }

    public record ExpiringWarranties(int windowDays, List<ExpiringWarranty> units) {
    }
}
