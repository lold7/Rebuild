package com.lowcortisol.backend.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ResetPasswordRequest(
    @NotBlank @Size(min = 8, max = 72) String newPassword,
    @NotBlank @Size(max = 72) String confirmPassword
) {
    @JsonIgnore
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return Objects.equals(newPassword, confirmPassword);
    }

    @JsonIgnore
    @AssertTrue(message = "Password must contain at most 72 UTF-8 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
