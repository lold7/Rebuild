package com.lowcortisol.backend.auth;

record UserAccount(long userId, String username, String email, String passwordHash, String phone, String role) {
    UserResponse response() {
        return new UserResponse(userId, username, email, phone, role);
    }
}
