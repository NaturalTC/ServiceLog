package dev.servicelog.servicerecord;

import org.springframework.data.jpa.repository.EntityGraph;
import dev.servicelog.equipment.EquipmentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {

    @EntityGraph(attributePaths = {"equipment", "equipment.property", "equipment.property.customer"})
    Optional<ServiceRecord> findDetailedById(Long id);

    // Newest first; same-day visits in the order they were recorded (uses the V6 index)
    List<ServiceRecord> findByEquipmentIdOrderByServiceDateDescIdDesc(Long equipmentId);

    // Repairs on other units of the same kind, newest first (for troubleshooting, S23)
    @Query("""
            select r from ServiceRecord r join fetch r.equipment e join fetch e.property p join fetch p.customer
            where e.type = :type and e.id <> :equipmentId
              and r.kind = dev.servicelog.servicerecord.ServiceKind.REPAIR
            order by r.serviceDate desc, r.id desc""")
    List<ServiceRecord> findRepairsOnOtherUnitsOfType(EquipmentType type, Long equipmentId, Pageable page);

    // Follow-ups noted on recent visits, newest first (for the assistant, S33)
    @EntityGraph(attributePaths = {"equipment", "equipment.property", "equipment.property.customer"})
    List<ServiceRecord> findTop30ByFollowUpNeededTrueAndServiceDateGreaterThanEqualOrderByServiceDateDescIdDesc(
            LocalDate since);
}
