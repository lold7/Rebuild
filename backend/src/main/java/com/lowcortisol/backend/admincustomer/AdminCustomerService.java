package com.lowcortisol.backend.admincustomer;

import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN') && #email == authentication.name")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminCustomerService {
    private static final int PAGE_SIZE = 15;
    private final AdminCustomerRepository customers;

    public AdminCustomerService(AdminCustomerRepository customers) { this.customers = customers; }

    public AdminCustomerPage list(String email, String page, String query, String activity, String sort) {
        return customers.page(page(page), PAGE_SIZE, query(query),
            option(activity, "all", Set.of("all", "ordered", "no-orders"), "activity"),
            option(sort, "newest", Set.of("newest", "orders", "spent"), "sort"));
    }

    public AdminCustomerSummary summary(String email, String query, String activity) {
        return customers.summary(query(query), option(activity, "all", Set.of("all", "ordered", "no-orders"), "activity"));
    }

    public AdminCustomerResponse get(String email, String id) {
        long customerId;
        try {
            if (id == null || !id.matches("[0-9]+")) throw new NumberFormatException();
            customerId = Long.parseLong(id);
            if (customerId < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) { throw AdminCustomerException.invalid("Customer ID must be a positive 64-bit integer"); }
        return customers.get(customerId).orElseThrow(AdminCustomerException::missing);
    }

    private int page(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number < 1) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) { throw AdminCustomerException.invalid("page must be a positive 32-bit integer"); }
    }

    private String query(String value) {
        String query = value == null ? "" : value.trim();
        if (query.length() > 255) throw AdminCustomerException.invalid("q must contain at most 255 characters");
        return query;
    }

    private String option(String value, String fallback, Set<String> allowed, String name) {
        String normalized = value == null ? fallback : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw AdminCustomerException.invalid("Invalid " + name + " filter");
        return normalized;
    }
}
