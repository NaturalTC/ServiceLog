package dev.servicelog.servicerecord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class ServiceRecordDtos {

    private ServiceRecordDtos() {
    }

    public record ServiceRecordRequest(
            @PastOrPresent(message = "can't be in the future") LocalDate serviceDate, // null means today
            @NotNull ServiceKind kind,
            @NotBlank @Size(max = 4000) String workDone,
            @Size(max = 2000) String partsUsed,
            @Size(max = 4000) String notes,
            Boolean followUpNeeded, // optional; not sent means no follow-up
            @Size(max = 500) String followUpNote) {

        public boolean needsFollowUp() {
            return Boolean.TRUE.equals(followUpNeeded);
        }
    }

    public record ServiceRecordResponse(Long id, Long equipmentId, LocalDate serviceDate, ServiceKind kind,
                                        String kindLabel, String workDone, String partsUsed, String notes,
                                        boolean followUpNeeded, String followUpNote) {

        static ServiceRecordResponse from(ServiceRecord r) {
            return new ServiceRecordResponse(r.getId(), r.getEquipment().getId(), r.getServiceDate(), r.getKind(),
                    r.getKind().getLabel(), r.getWorkDone(), r.getPartsUsed(), r.getNotes(), r.isFollowUpNeeded(),
                    r.getFollowUpNote());
        }
    }

    // The record page also shows which unit and whose it is
    public record ServiceRecordDetailResponse(ServiceRecordResponse record, String equipmentLabel, Long customerId,
                                              String customerName, String address) {
    }

    public record ServiceKindOption(ServiceKind value, String label) {
    }
}
