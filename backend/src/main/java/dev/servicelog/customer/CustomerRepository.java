package dev.servicelog.customer;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Load properties in the same query, avoiding one extra query per customer
    @EntityGraph(attributePaths = "properties")
    List<Customer> findAllByOrderByNameAscIdAsc();

    @EntityGraph(attributePaths = "properties")
    Optional<Customer> findWithPropertiesById(Long id);
}
