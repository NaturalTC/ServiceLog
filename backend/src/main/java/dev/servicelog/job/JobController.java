package dev.servicelog.job;

import dev.servicelog.job.JobDtos.JobRequest;
import dev.servicelog.job.JobDtos.JobResponse;
import dev.servicelog.job.JobDtos.JobTypeOption;
import dev.servicelog.job.JobDtos.OverlapResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

// All routes require login (see SecurityConfig)
@RestController
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/api/job-types")
    public List<JobTypeOption> types() {
        return Arrays.stream(JobType.values()).map(t -> new JobTypeOption(t, t.getLabel())).toList();
    }

    // Scheduled jobs between two dates (default: the next 7 days from today), in time order
    @GetMapping("/api/jobs")
    public List<JobResponse> scheduled(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return jobService.scheduled(from, to);
    }

    // The home screen: today's scheduled jobs, in time order
    @GetMapping("/api/jobs/today")
    public JobDtos.TodayResponse today() {
        return jobService.today();
    }

    @GetMapping("/api/customers/{customerId}/jobs")
    public List<JobResponse> upcomingForCustomer(@PathVariable long customerId) {
        return jobService.upcomingForCustomer(customerId);
    }

    @PostMapping("/api/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody JobRequest request) {
        return jobService.create(request);
    }

    @GetMapping("/api/jobs/{id}")
    public JobResponse get(@PathVariable long id) {
        return jobService.get(id);
    }

    @PutMapping("/api/jobs/{id}")
    public JobResponse update(@PathVariable long id, @Valid @RequestBody JobRequest request) {
        return jobService.update(id, request);
    }

    @PostMapping("/api/jobs/{id}/cancel")
    public JobResponse cancel(@PathVariable long id) {
        return jobService.cancel(id);
    }

    @ExceptionHandler(JobOverlapException.class)
    public ResponseEntity<OverlapResponse> handleOverlap(JobOverlapException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new OverlapResponse("JOB_OVERLAP", ex.getMessage(), ex.getConflicts()));
    }
}
