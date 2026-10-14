package com.lowcortisol.backend.order;

import com.lowcortisol.backend.address.AddressService;
import com.lowcortisol.backend.cart.CartService;
import com.lowcortisol.backend.payment.PaymentMethodService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CUSTOMER') && #email == authentication.name")
public class CheckoutService {
    static final List<String> PAYMENT_METHODS = List.of("CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER", "DIGITAL_WALLET");
    private final CartService cart;
    private final AddressService addresses;
    private final PaymentMethodService payments;

    public CheckoutService(CartService cart, AddressService addresses, PaymentMethodService payments) {
        this.cart = cart;
        this.addresses = addresses;
        this.payments = payments;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CheckoutResponse preview(String email) {
        var currentCart = cart.view(email);
        var addressBook = addresses.list(email);
        return new CheckoutResponse(currentCart, addressBook, payments.list(email, null), PAYMENT_METHODS, "USD",
            currentCart.valid() && addressBook.totalAddresses() > 0);
    }
}
