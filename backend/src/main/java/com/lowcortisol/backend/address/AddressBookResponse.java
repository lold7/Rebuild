package com.lowcortisol.backend.address;

import java.util.List;

public record AddressBookResponse(List<AddressResponse> addresses, int totalAddresses, Long defaultAddressId) {}
