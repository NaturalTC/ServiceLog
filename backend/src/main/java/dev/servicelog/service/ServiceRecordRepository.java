package dev.servicelog.service;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {

    @EntityGraph(attributePaths = {"equipment", "equipment.property", "equipment.property.customer"})
    Optional<ServiceRecord> findDetailedById(Long id);

    // Newest first; same-day visits in the order they were recorded (uses the V6 index)
    List<ServiceRecord> findByEquipmentIdOrderByServiceDateDescIdDesc(Long equipmentId);
}
