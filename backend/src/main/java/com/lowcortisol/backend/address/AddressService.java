package com.lowcortisol.backend.address;

import com.lowcortisol.backend.auth.CustomerAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') && #email == authentication.name")
public class AddressService {
    private final AddressRepository addresses;
    private final CustomerAccountRepository customers;

    public AddressService(AddressRepository addresses, CustomerAccountRepository customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public AddressBookResponse list(String email) {
        return book(customers.customerId(email, false));
    }

    @Transactional(readOnly = true)
    public AddressResponse get(String email, long addressId) {
        validateId(addressId);
        return owned(customers.customerId(email, false), addressId);
    }

    @Transactional
    public AddressResponse add(String email, AddAddressRequest request) {
        long userId = customers.customerId(email, true);
        boolean isDefault = request.isDefault() || !addresses.hasDefault(userId);
        if (isDefault) addresses.clearDefault(userId);
        return addresses.add(userId, request, isDefault);
    }

    @Transactional
    public AddressBookResponse delete(String email, long addressId) {
        validateId(addressId);
        long userId = customers.customerId(email, true);
        owned(userId, addressId);
        addresses.delete(userId, addressId);
        if (!addresses.hasDefault(userId)) addresses.promoteNewest(userId);
        return book(userId);
    }

    @Transactional
    public AddressBookResponse setDefault(String email, long addressId) {
        validateId(addressId);
        long userId = customers.customerId(email, true);
        AddressResponse address = owned(userId, addressId); // Check ownership before clearing the current default.
        if (!address.isDefault()) {
            addresses.clearDefault(userId);
            addresses.setDefault(userId, addressId);
        }
        return book(userId);
    }

    private AddressResponse owned(long userId, long addressId) {
        return addresses.ownedAddress(userId, addressId).orElseThrow(AddressNotFoundException::new);
    }

    private AddressBookResponse book(long userId) {
        var items = addresses.list(userId);
        Long defaultId = items.stream().filter(AddressResponse::isDefault).map(AddressResponse::addressId).findFirst().orElse(null);
        return new AddressBookResponse(items, items.size(), defaultId);
    }

    private void validateId(long id) {
        if (id < 1) throw new InvalidAddressRequestException();
    }
}
