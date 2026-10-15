package com.lowcortisol.backend.payment;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodService methods;

    public PaymentMethodController(PaymentMethodService methods) {
        this.methods = methods;
    }

    @GetMapping
    public PaymentMethodsResponse list(@RequestParam(required = false) String type, Authentication authentication) {
        return methods.list(authentication.getName(), type);
    }

    @GetMapping("/{id}")
    public PaymentMethodResponse get(@PathVariable String id, Authentication authentication) {
        return methods.get(authentication.getName(), id(id));
    }

    @PostMapping("/add")
    public ResponseEntity<PaymentMethodResponse> add(@Valid @RequestBody AddPaymentMethodRequest request, Authentication authentication) {
        PaymentMethodResponse method = methods.add(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/account/payment-methods/" + method.paymentMethodId())).body(method);
    }

    @PostMapping("/delete/{id}")
    public PaymentMethodsResponse delete(@PathVariable String id, Authentication authentication) {
        return methods.delete(authentication.getName(), id(id));
    }

    @PostMapping("/default/{id}")
    public PaymentMethodsResponse setDefault(@PathVariable String id, Authentication authentication) {
        return methods.setDefault(authentication.getName(), id(id));
    }

    private long id(String value) {
        try {
            if (!value.matches("\\d+")) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw PaymentMethodException.invalid("Invalid payment method ID");
        }
    }
}
