package com.lowcortisol.backend.address;

public class AddressNotFoundException extends RuntimeException {
    public AddressNotFoundException() {
        super("This address does not exist.");
    }
}
