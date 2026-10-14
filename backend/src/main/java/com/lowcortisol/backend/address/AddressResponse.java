package com.lowcortisol.backend.address;

import java.time.OffsetDateTime;

public record AddressResponse(long addressId, String label, String fullName, String phone, String addressLine,
    String city, String province, String postalCode, boolean isDefault, OffsetDateTime createdAt) {}
