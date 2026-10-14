package com.lowcortisol.backend.order;

import com.lowcortisol.backend.address.AddressBookResponse;
import com.lowcortisol.backend.cart.CartResponse;
import com.lowcortisol.backend.payment.PaymentMethodsResponse;
import java.util.List;

public record CheckoutResponse(CartResponse cart, AddressBookResponse addressBook,
    PaymentMethodsResponse savedPaymentMethods, List<String> paymentMethods, String currency, boolean ready) {}
