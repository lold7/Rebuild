package com.lowcortisol.backend.address;

public class InvalidAddressRequestException extends RuntimeException {
    public InvalidAddressRequestException() {
        super("addressId must be a positive 64-bit integer.");
    }
}
