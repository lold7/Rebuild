package com.lowcortisol.backend.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ChangePasswordRequest(
    @NotBlank @Size(max = 72) String currentPassword,
    @NotBlank @Size(min = 8, max = 72) String newPassword,
    @NotBlank @Size(max = 72) String confirmPassword
) {
    @JsonIgnore
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return Objects.equals(newPassword, confirmPassword);
    }

    @JsonIgnore
    @AssertTrue(message = "Passwords must contain at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return withinLimit(currentPassword) && withinLimit(newPassword);
    }

    private boolean withinLimit(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
