package dev.servicelog.tuneup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TuneUpReminderRepository extends JpaRepository<TuneUpReminder, Long> {

    List<TuneUpReminder> findByCustomerIdOrderBySentAtDesc(Long customerId);

    Optional<TuneUpReminder> findFirstByPropertyIdAndStatusOrderBySentAtDesc(Long propertyId,
                                                                            TuneUpReminder.Status status);

    // Latest successful reminder per address, for the due list: [propertyId, sentAt]
    @Query("""
            select r.propertyId, max(r.sentAt) from TuneUpReminder r
            where r.status = dev.servicelog.tuneup.TuneUpReminder.Status.SENT and r.sentAt >= :since
            group by r.propertyId""")
    List<Object[]> latestSentPerPropertySince(Instant since);
}
