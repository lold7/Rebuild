package com.lowcortisol.backend.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
    @NotBlank @Size(max = 100) String username,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @Size(max = 20) String phone
) {
    // BCrypt's limit is measured in bytes, including multi-byte Unicode characters.
    @JsonIgnore
    @AssertTrue(message = "Password must contain at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
