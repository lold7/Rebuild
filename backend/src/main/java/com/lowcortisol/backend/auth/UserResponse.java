package com.lowcortisol.backend.auth;

public record UserResponse(long userId, String username, String email, String phone, String role) {}
