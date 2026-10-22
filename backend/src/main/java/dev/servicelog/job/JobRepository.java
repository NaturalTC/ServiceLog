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

    // Scheduled jobs in a date range, in the order they happen
    @EntityGraph(attributePaths = {"property", "property.customer"})
    @Query("""
            select j from Job j
            where j.scheduledDate between :from and :to and j.status = dev.servicelog.job.JobStatus.SCHEDULED
            order by j.scheduledDate, j.startTime, j.id""")
    List<Job> findScheduledBetween(LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"property", "property.customer"})
    @Query("""
            select j from Job j
            where j.property.customer.id = :customerId and j.scheduledDate >= :from
              and j.status = dev.servicelog.job.JobStatus.SCHEDULED
            order by j.scheduledDate, j.startTime, j.id""")
    List<Job> findUpcomingForCustomer(Long customerId, LocalDate from);
}
