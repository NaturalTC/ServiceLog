package dev.servicelog.job;

import dev.servicelog.config.BusinessCalendar;
import dev.servicelog.customer.Property;
import dev.servicelog.customer.PropertyRepository;
import dev.servicelog.exception.ConflictException;
import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.exception.NotFoundException;
import dev.servicelog.job.JobDtos.JobRequest;
import dev.servicelog.job.JobDtos.JobResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JobService {

    static final int DEFAULT_DURATION_MINUTES = 60;
    static final int MAX_RANGE_DAYS = 92;

    private final JobRepository jobs;
    private final PropertyRepository properties;
    private final BusinessCalendar calendar;

    public JobService(JobRepository jobs, PropertyRepository properties, BusinessCalendar calendar) {
        this.jobs = jobs;
        this.properties = properties;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public List<JobResponse> scheduled(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : calendar.today();
        LocalDate end = to != null ? to : start.plusDays(6);
        if (end.isBefore(start)) {
            throw new InvalidRequestException("to: must be on or after from");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("Date range can be at most " + MAX_RANGE_DAYS + " days");
        }
        return jobs.findScheduledBetween(start, end).stream().map(JobService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobDtos.TodayResponse today() {
        LocalDate today = calendar.today();
        return new JobDtos.TodayResponse(today, scheduled(today, today));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> upcomingForCustomer(long customerId) {
        return jobs.findUpcomingForCustomer(customerId, calendar.today()).stream()
                .map(JobService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse get(long id) {
        return toResponse(find(id));
    }

    @Transactional
    public JobResponse create(JobRequest request) {
        Property property = property(request.propertyId());
        Job job = new Job(property, request.date(), request.startTime(), duration(request), request.type(),
                urgency(request), clean(request.notes()));
        checkOverlap(job, request);
        return toResponse(jobs.save(job));
    }

    // Rescheduling is an update with a new date and/or time
    @Transactional
    public JobResponse update(long id, JobRequest request) {
        Job job = find(id);
        if (job.getStatus() == JobStatus.CANCELLED) {
            throw new ConflictException("This job was cancelled. Schedule a new one instead.");
        }
        if (job.getStatus() == JobStatus.DONE) {
            throw new ConflictException("This job is already done and can't be changed.");
        }
        job.update(property(request.propertyId()), request.date(), request.startTime(), duration(request),
                request.type(), urgency(request), clean(request.notes()));
        checkOverlap(job, request);
        return toResponse(jobs.saveAndFlush(job));
    }

    @Transactional
    public JobResponse cancel(long id) {
        Job job = find(id);
        if (!job.getStatus().isOpen()) {
            throw new ConflictException("Only a scheduled or in-progress job can be cancelled.");
        }
        job.cancel();
        return toResponse(jobs.saveAndFlush(job));
    }

    @Transactional
    public JobResponse start(long id) {
        Job job = find(id);
        if (job.getStatus() != JobStatus.SCHEDULED) {
            throw new ConflictException("Only a scheduled job can be started.");
        }
        job.markInProgress();
        return toResponse(jobs.saveAndFlush(job));
    }

    // A one-person business can't be in two places at once: warn before double booking.
    // Jobs that start the day before can run past midnight, so look one day back.
    private void checkOverlap(Job job, JobRequest request) {
        if (request.overlapConfirmed()) {
            return;
        }
        LocalDateTime start = job.start();
        LocalDateTime end = job.end();
        List<JobResponse> conflicts = jobs.findScheduledBetween(start.toLocalDate().minusDays(1), end.toLocalDate())
                .stream()
                .filter(other -> !other.getId().equals(job.getId()))
                .filter(other -> other.overlaps(start, end))
                .map(JobService::toResponse)
                .toList();
        if (!conflicts.isEmpty()) {
            throw new JobOverlapException(conflicts);
        }
    }

    private Job find(long id) {
        return jobs.findDetailedById(id).orElseThrow(() -> new NotFoundException("Job not found"));
    }

    private Property property(long propertyId) {
        return properties.findById(propertyId).orElseThrow(() -> new NotFoundException("Property not found"));
    }

    private static int duration(JobRequest request) {
        return request.durationMinutes() != null ? request.durationMinutes() : DEFAULT_DURATION_MINUTES;
    }

    private static JobUrgency urgency(JobRequest request) {
        return request.urgency() != null ? request.urgency() : JobUrgency.NORMAL;
    }

    static JobResponse toResponse(Job j) {
        Property p = j.getProperty();
        String address = p.getStreet() + (p.getUnit() != null ? ", " + p.getUnit() : "") + ", " + p.getCity() + ", "
                + p.getState() + " " + p.getZip();
        return new JobResponse(j.getId(), j.getScheduledDate(), j.getStartTime(), j.getDurationMinutes(), j.getType(),
                j.getType().getLabel(), j.getUrgency(), j.getStatus(), j.getNotes(), p.getId(), address,
                p.getCustomer().getId(), p.getCustomer().getName(), p.getCustomer().getPhone(), j.getCompletedAt());
    }

    private static String clean(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
