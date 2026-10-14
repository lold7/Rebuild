package com.lowcortisol.backend.address;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/addresses")
public class AddressController {
    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public AddressBookResponse list(Authentication authentication) {
        return addresses.list(authentication.getName());
    }

    @GetMapping("/{id}")
    public AddressResponse get(@PathVariable String id, Authentication authentication) {
        return addresses.get(authentication.getName(), id(id));
    }

    @PostMapping("/add")
    public ResponseEntity<AddressResponse> add(@Valid @RequestBody AddAddressRequest request, Authentication authentication) {
        AddressResponse address = addresses.add(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/account/addresses/" + address.addressId())).body(address);
    }

    @PostMapping("/delete/{id}")
    public AddressBookResponse delete(@PathVariable String id, Authentication authentication) {
        return addresses.delete(authentication.getName(), id(id));
    }

    @PostMapping("/default/{id}")
    public AddressBookResponse setDefault(@PathVariable String id, Authentication authentication) {
        return addresses.setDefault(authentication.getName(), id(id));
    }

    private long id(String value) {
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidAddressRequestException();
        }
    }
}
