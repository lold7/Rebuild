package com.lowcortisol.backend.admincustomer;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {
    private final AdminCustomerService customers;

    public AdminCustomerController(AdminCustomerService customers) { this.customers = customers; }

    @GetMapping
    public AdminCustomerPage list(@RequestParam(defaultValue = "1") String page, @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) String activity, @RequestParam(required = false) String sort, Authentication auth) {
        return customers.list(auth.getName(), page, q, activity, sort);
    }

    @GetMapping("/summary")
    public AdminCustomerSummary summary(@RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) String activity, Authentication auth) {
        return customers.summary(auth.getName(), q, activity);
    }

    @GetMapping("/{id}")
    public AdminCustomerResponse get(@PathVariable String id, Authentication auth) { return customers.get(auth.getName(), id); }
}
