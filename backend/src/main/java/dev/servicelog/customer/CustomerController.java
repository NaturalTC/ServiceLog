package dev.servicelog.customer;

import dev.servicelog.customer.CustomerDtos.CustomerRequest;
import dev.servicelog.customer.CustomerDtos.CustomerResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// All routes require login (see SecurityConfig)
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // With ?q=..., searches name, street and phone (up to 50 results); without it, lists everyone
    @GetMapping
    public List<CustomerResponse> list(@RequestParam(required = false) String q) {
        return q == null || q.isBlank() ? customerService.list() : customerService.search(q);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable long id) {
        return customerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }
}
