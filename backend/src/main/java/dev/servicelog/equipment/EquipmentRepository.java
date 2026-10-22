package dev.servicelog.equipment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    @EntityGraph(attributePaths = {"property", "property.customer"})
    Optional<Equipment> findDetailedById(Long id);

    @Query("select e from Equipment e where e.property.customer.id = :customerId order by e.property.id, e.id")
    List<Equipment> findAllForCustomer(Long customerId);

    // Case-insensitive, matching the unique index on lower(serial_number)
    @EntityGraph(attributePaths = {"property", "property.customer"})
    Optional<Equipment> findFirstBySerialNumberIgnoreCase(String serialNumber);

}
