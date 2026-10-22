package dev.servicelog.tuneup;

import dev.servicelog.equipment.Equipment;
import dev.servicelog.equipment.EquipmentType;
import dev.servicelog.job.Job;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

// The two lookups the due list needs, each a single query
@Repository
public class TuneUpQueries {

    private final EntityManager em;

    public TuneUpQueries(EntityManager em) {
        this.em = em;
    }

    // Seasonal equipment with the date of its last tune-up or installation (null if neither)
    public List<Object[]> seasonalEquipmentWithLastTuneUp(Collection<EquipmentType> types) {
        return em.createQuery("""
                        select e, max(r.serviceDate)
                        from Equipment e
                        join fetch e.property p
                        join fetch p.customer
                        left join ServiceRecord r on r.equipment = e
                            and r.kind in (dev.servicelog.servicerecord.ServiceKind.TUNE_UP,
                                           dev.servicelog.servicerecord.ServiceKind.INSTALLATION)
                        where e.type in :types
                        group by e, p, p.customer""", Object[].class)
                .setParameter("types", types)
                .getResultList();
    }

    // Tune-ups already on the calendar from today on, so nobody gets booked twice
    public List<Job> openTuneUpsFrom(LocalDate today) {
        return em.createQuery("""
                        select j from Job j
                        where j.type = dev.servicelog.job.JobType.TUNE_UP
                          and j.status in (dev.servicelog.job.JobStatus.SCHEDULED, dev.servicelog.job.JobStatus.IN_PROGRESS)
                          and j.scheduledDate >= :today
                        order by j.scheduledDate, j.startTime""", Job.class)
                .setParameter("today", today)
                .getResultList();
    }

    static Equipment equipment(Object[] row) {
        return (Equipment) row[0];
    }

    static LocalDate lastTuneUp(Object[] row) {
        return (LocalDate) row[1];
    }
}
