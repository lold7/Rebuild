package com.lowcortisol.backend.adminorder;

public record OrderCustomerResponse(long userId, String username, String email, String phone) {}
