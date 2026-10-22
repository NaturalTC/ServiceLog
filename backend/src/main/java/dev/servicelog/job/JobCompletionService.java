package dev.servicelog.job;

import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentRepository;
import dev.servicelog.exception.ConflictException;
import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.exception.NotFoundException;
import dev.servicelog.job.JobDtos.CompleteJobRequest;
import dev.servicelog.job.JobDtos.CompleteJobResponse;
import dev.servicelog.servicerecord.ServiceRecord;
import dev.servicelog.servicerecord.ServiceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Finishing a job: marks it done and writes a service record on each piece of equipment
// the owner worked on. All or nothing, in one transaction.
@Service
public class JobCompletionService {

    private final JobRepository jobs;
    private final EquipmentRepository equipment;
    private final ServiceRecordRepository serviceRecords;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public JobCompletionService(JobRepository jobs, EquipmentRepository equipment,
                                ServiceRecordRepository serviceRecords, BusinessCalendar calendar, Clock clock) {
        this.jobs = jobs;
        this.equipment = equipment;
        this.serviceRecords = serviceRecords;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional
    public CompleteJobResponse complete(long jobId, CompleteJobRequest request) {
        Job job = jobs.findDetailedById(jobId).orElseThrow(() -> new NotFoundException("Job not found"));
        if (!job.getStatus().isOpen()) {
            throw new ConflictException(job.getStatus() == JobStatus.DONE
                    ? "This job is already done."
                    : "This job was cancelled and can't be finished.");
        }

        List<Long> ids = request.equipmentIds() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(request.equipmentIds())); // ignore duplicates, keep order
        String workDone = clean(request.workDone());
        if (!ids.isEmpty() && workDone == null) {
            throw new InvalidRequestException("workDone: describe the work done");
        }

        // Every unit must be at this job's address
        Map<Long, Equipment> units = equipment.findAllById(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        for (Long id : ids) {
            Equipment unit = units.get(id);
            if (unit == null || !unit.getProperty().getId().equals(job.getProperty().getId())) {
                throw new InvalidRequestException("Equipment " + id + " isn't at this job's address");
            }
        }

        // The visit happened on the job's date, unless a future job is being finished early
        LocalDate today = calendar.today();
        LocalDate serviceDate = job.getScheduledDate().isAfter(today) ? today : job.getScheduledDate();
        boolean followUp = Boolean.TRUE.equals(request.followUpNeeded());

        List<Long> recordIds = ids.stream().map(id -> {
            ServiceRecord record = new ServiceRecord(units.get(id), serviceDate, job.getType().getServiceKind(),
                    workDone, clean(request.partsUsed()), clean(request.notes()), followUp,
                    clean(request.followUpNote()));
            record.linkToJob(job.getId());
            return serviceRecords.save(record).getId();
        }).toList();

        job.complete(Instant.now(clock));
        return new CompleteJobResponse(JobService.toResponse(jobs.saveAndFlush(job)), recordIds);
    }

    private static String clean(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
