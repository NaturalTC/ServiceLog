package dev.servicelog.customer;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @Query("select count(e) > 0 from Equipment e where e.property.id in :propertyIds")
    boolean anyHaveEquipment(Collection<Long> propertyIds);

    @Query("select count(j) > 0 from Job j where j.property.id in :propertyIds")
    boolean anyHaveJobs(Collection<Long> propertyIds);

    // Addresses to look up on the map: new or changed ones, and ones not found last time
    // (the service can briefly answer "no match" for an address it knows), oldest first
    @Query("""
            select p from Property p
            where p.geocodedAt is null or (p.latitude is null and p.geocodedAt < :retryNotFoundBefore)
            order by p.id""")
    List<Property> findToGeocode(Instant retryNotFoundBefore, Pageable page);
}
