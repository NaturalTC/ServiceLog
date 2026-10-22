package dev.servicelog.equipment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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
    // Installed units (install date known) not registered for warranty yet, newest install first
    @Query("""
            select e from Equipment e join fetch e.property p join fetch p.customer
            where e.warrantyRegistered = false and e.installDate is not null
            order by e.installDate desc, e.id desc""")
    List<Equipment> findUnregisteredInstalls();

    // Units with either warranty ending in [from, to]; the caller works out which date is the last day of cover
    @Query("""
            select e from Equipment e join fetch e.property p join fetch p.customer
            where e.warrantyEndsOn between :from and :to or e.extendedWarrantyEndsOn between :from and :to""")
    List<Equipment> findWithWarrantyEndingBetween(LocalDate from, LocalDate to);
}
