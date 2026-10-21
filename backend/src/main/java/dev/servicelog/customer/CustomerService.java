package dev.servicelog.customer;

import dev.servicelog.customer.CustomerDtos.CustomerRequest;
import dev.servicelog.customer.CustomerDtos.CustomerResponse;
import dev.servicelog.customer.CustomerDtos.PropertyRequest;
import dev.servicelog.exception.InvalidRequestException;
import dev.servicelog.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customers.findAllByOrderByNameAscIdAsc().stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(long id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (request.properties().stream().anyMatch(p -> p.id() != null)) {
            throw new InvalidRequestException("New properties can't have an id");
        }
        Customer customer = new Customer(clean(request.name()), phone(request.phone()), cleanEmail(request.email()),
                clean(request.notes()));
        request.properties().forEach(p -> customer.addProperty(newProperty(p)));
        return CustomerResponse.from(customers.save(customer));
    }

    // Replaces the customer's details and property list with what the form sent:
    // properties with an id are updated, ones without are added, and ones left out are removed.
    @Transactional
    public CustomerResponse update(long id, CustomerRequest request) {
        Customer customer = find(id);
        customer.updateDetails(clean(request.name()), phone(request.phone()), cleanEmail(request.email()),
                clean(request.notes()));

        Map<Long, Property> existing = customer.getProperties().stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));
        Set<Long> kept = new HashSet<>();

        for (PropertyRequest p : request.properties()) {
            if (p.id() == null) {
                customer.addProperty(newProperty(p));
            } else {
                Property property = existing.get(p.id());
                if (property == null) {
                    throw new InvalidRequestException("Property " + p.id() + " doesn't belong to this customer");
                }
                property.update(clean(p.street()), clean(p.unit()), clean(p.city()), state(p.state()), p.zip().trim(),
                        clean(p.notes()));
                kept.add(p.id());
            }
        }
        existing.values().stream()
                .filter(property -> !kept.contains(property.getId()))
                .toList()
                .forEach(customer::removeProperty);

        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    private Customer find(long id) {
        return customers.findWithPropertiesById(id)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private Property newProperty(PropertyRequest p) {
        return new Property(clean(p.street()), clean(p.unit()), clean(p.city()), state(p.state()), p.zip().trim(),
                clean(p.notes()));
    }

    private static String phone(String input) {
        return PhoneNumbers.normalize(input)
                .orElseThrow(() -> new InvalidRequestException("phone: must be a 10-digit U.S. number"));
    }

    private static String state(String input) {
        return input.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanEmail(String input) {
        String email = clean(input);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    // Trims text and turns blank optional fields into null, so "" and "  " aren't stored
    private static String clean(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
