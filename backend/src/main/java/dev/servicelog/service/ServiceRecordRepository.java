package dev.servicelog.service;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {

    @EntityGraph(attributePaths = {"equipment", "equipment.property", "equipment.property.customer"})
    Optional<ServiceRecord> findDetailedById(Long id);
}
