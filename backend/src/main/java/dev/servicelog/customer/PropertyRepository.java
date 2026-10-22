package dev.servicelog.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @Query("select count(e) > 0 from Equipment e where e.property.id in :propertyIds")
    boolean anyHaveEquipment(Collection<Long> propertyIds);

    @Query("select count(j) > 0 from Job j where j.property.id in :propertyIds")
    boolean anyHaveJobs(Collection<Long> propertyIds);
}
