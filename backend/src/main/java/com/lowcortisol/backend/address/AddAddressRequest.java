package com.lowcortisol.backend.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddAddressRequest(
    @Size(max = 50) String label,
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 20) String phone,
    @NotBlank @Size(max = 500) String addressLine,
    @Size(max = 100) String city,
    @Size(max = 100) String province,
    @Size(max = 10) String postalCode,
    boolean isDefault
) {}
