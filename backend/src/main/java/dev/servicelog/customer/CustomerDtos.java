package dev.servicelog.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// Request and response bodies for /api/customers.
// Phone numbers are checked and normalized in CustomerService, which accepts the many
// ways people type them: (413) 555-1234, 413.555.1234, +1 413 555 1234.
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record PropertyRequest(
            Long id, // set when editing an existing property; null for a new one
            @NotBlank @Size(max = 200) String street,
            @Size(max = 50) String unit,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be a 2-letter state code") String state,
            @NotBlank @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "must be 5 digits (or ZIP+4)") String zip,
            @Size(max = 2000) String notes) {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 2000) String notes,
            @NotEmpty(message = "must include at least one property") List<@Valid PropertyRequest> properties) {
    }

    public record PropertyResponse(Long id, String street, String unit, String city, String state, String zip,
                                   String notes) {

        static PropertyResponse from(Property p) {
            return new PropertyResponse(p.getId(), p.getStreet(), p.getUnit(), p.getCity(), p.getState(), p.getZip(),
                    p.getNotes());
        }
    }

    public record CustomerResponse(Long id, String name, String phone, String email, String notes,
                                   List<PropertyResponse> properties) {

        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail(), c.getNotes(),
                    c.getProperties().stream().map(PropertyResponse::from).toList());
        }
    }
}
