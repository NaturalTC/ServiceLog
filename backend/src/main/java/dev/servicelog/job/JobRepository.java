package dev.servicelog.job;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    @EntityGraph(attributePaths = {"property", "property.customer"})
    Optional<Job> findDetailedById(Long id);

    // Jobs on the calendar in a date range (anything not cancelled), in the order they happen
    @EntityGraph(attributePaths = {"property", "property.customer"})
    @Query("""
            select j from Job j
            where j.scheduledDate between :from and :to and j.status <> dev.servicelog.job.JobStatus.CANCELLED
            order by j.scheduledDate, j.startTime, j.id""")
    List<Job> findScheduledBetween(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"property", "property.customer"})
    @Query("""
            select j from Job j
            where j.property.customer.id = :customerId and j.scheduledDate >= :from
              and j.status in (dev.servicelog.job.JobStatus.SCHEDULED, dev.servicelog.job.JobStatus.IN_PROGRESS)
            order by j.scheduledDate, j.startTime, j.id""")
    List<Job> findUpcomingForCustomer(Long customerId, LocalDate from);
}
