package com.lowcortisol.backend.auth;

public record ProfileUpdateResponse(UserResponse user, boolean reauthenticationRequired) {}
