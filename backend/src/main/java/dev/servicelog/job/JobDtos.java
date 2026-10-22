package dev.servicelog.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobRequest(
            @NotNull Long propertyId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @Min(15) @Max(720) Integer durationMinutes, // optional; defaults to 60
            @NotNull JobType type,
            JobUrgency urgency, // optional; defaults to NORMAL
            @Size(max = 2000) String notes,
            Boolean allowOverlap) { // set after the owner confirms a double booking

        boolean overlapConfirmed() {
            return Boolean.TRUE.equals(allowOverlap);
        }
    }

    public record JobResponse(Long id, LocalDate date, LocalTime startTime, int durationMinutes, JobType type,
                              String typeLabel, JobUrgency urgency, JobStatus status, String notes,
                              Long propertyId, String address, Long customerId, String customerName,
                              String customerPhone) {
    }

    // "My day": the business's today (not the server's or the phone's) and its jobs in time order
    public record TodayResponse(LocalDate date, List<JobResponse> jobs) {
    }

    // Returned with 409 when a job would overlap others, so the app can ask "book anyway?"
    public record OverlapResponse(String code, String message, List<JobResponse> conflicts) {
    }

    public record JobTypeOption(JobType value, String label) {
    }
}
