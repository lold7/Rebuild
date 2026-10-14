package com.lowcortisol.backend.payment;

import com.lowcortisol.backend.auth.CustomerAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') && #email == authentication.name")
public class PaymentMethodService {
    private static final Set<String> TYPES = Set.of("CREDIT", "DEBIT");
    private static final Set<String> BRANDS = Set.of("visa", "mastercard", "amex", "discover", "jcb", "unionpay", "diners", "other");
    private final PaymentMethodRepository methods;
    private final CustomerAccountRepository customers;

    public PaymentMethodService(PaymentMethodRepository methods, CustomerAccountRepository customers) {
        this.methods = methods;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public PaymentMethodsResponse list(String email, String type) {
        String filter = type == null ? null : type(type);
        return book(customers.customerId(email, false), filter, today());
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse get(String email, long id) {
        validateId(id);
        return owned(customers.customerId(email, false), id).response(today());
    }

    @Transactional
    public PaymentMethodResponse add(String email, @NotNull @Valid AddPaymentMethodRequest request) {
        String type = type(request.type());
        String brand = request.brand().trim().toLowerCase(Locale.ROOT);
        if (!BRANDS.contains(brand)) throw PaymentMethodException.invalid("Unsupported card brand");
        String currency = request.currency() == null ? "USD" : request.currency().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw PaymentMethodException.invalid("Invalid currency code");
        }
        YearMonth today = today();
        if (YearMonth.of(request.expiryYear(), request.expiryMonth()).isBefore(today)) throw PaymentMethodException.expired();
        long userId = customers.customerId(email, true);
        boolean isDefault = request.isDefault() || methods.list(userId).stream().noneMatch(method -> method.isDefault() && method.active(today));
        if (isDefault) methods.clearDefault(userId);
        return methods.add(userId, request, type, brand, currency, isDefault).response(today);
    }

    @Transactional
    public PaymentMethodsResponse delete(String email, long id) {
        validateId(id);
        long userId = customers.customerId(email, true);
        PaymentMethod method = owned(userId, id);
        YearMonth today = today();
        if (method.active(today) && methods.list(userId).stream().filter(item -> item.active(today)).count() == 1) {
            throw PaymentMethodException.lastActive();
        }
        methods.delete(userId, id);
        var remaining = methods.list(userId);
        if (remaining.stream().noneMatch(item -> item.isDefault() && item.active(today))) {
            methods.clearDefault(userId);
            remaining.stream().filter(item -> item.active(today)).findFirst().ifPresent(item -> methods.setDefault(userId, item.id()));
        }
        return book(userId, null, today);
    }

    @Transactional
    public PaymentMethodsResponse setDefault(String email, long id) {
        validateId(id);
        long userId = customers.customerId(email, true);
        PaymentMethod method = owned(userId, id); // Verify ownership and expiry before clearing the current default.
        YearMonth today = today();
        if (!method.active(today)) throw PaymentMethodException.expired();
        if (!method.isDefault()) {
            methods.clearDefault(userId);
            methods.setDefault(userId, id);
        }
        return book(userId, null, today);
    }

    private PaymentMethod owned(long userId, long id) {
        return methods.owned(userId, id).orElseThrow(PaymentMethodException::notFound);
    }

    private PaymentMethodsResponse book(long userId, String type, YearMonth today) {
        var all = methods.list(userId);
        Long defaultId = all.stream().filter(PaymentMethod::isDefault).map(PaymentMethod::id).findFirst().orElse(null);
        var filtered = all.stream().filter(item -> type == null || type.equals(item.type())).map(item -> item.response(today)).toList();
        return new PaymentMethodsResponse(filtered, filtered.size(), defaultId);
    }

    private String type(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw PaymentMethodException.invalid("Type must be CREDIT or DEBIT");
        return normalized;
    }

    private void validateId(long id) {
        if (id < 1) throw PaymentMethodException.invalid("Invalid payment method ID");
    }

    private YearMonth today() { return YearMonth.now(ZoneOffset.UTC); }
}
