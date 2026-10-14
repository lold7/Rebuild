package com.lowcortisol.backend.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    private final PersistentTokenBasedRememberMeServices rememberMe;

    public PasswordRecoveryController(PasswordRecoveryService recovery, PersistentTokenBasedRememberMeServices rememberMe) {
        this.recovery = recovery;
        this.rememberMe = rememberMe;
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse request(@Valid @RequestBody ForgotPasswordRequest request) {
        String link = recovery.requestReset(request.email());
        return new ForgotPasswordResponse("If an account exists for that email, a password reset link has been requested.", link);
    }

    @GetMapping("/reset-password/{token}")
    public TokenStatusResponse validate(@PathVariable String token) {
        recovery.validateToken(token);
        return new TokenStatusResponse(true);
    }

    @PostMapping("/reset-password/{token}")
    public ResetPasswordResponse reset(@PathVariable String token, @Valid @RequestBody ResetPasswordRequest credentials,
        Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        String email = recovery.resetPassword(token, credentials.newPassword());
        if (authentication != null && authentication.getName().equals(email)) {
            rememberMe.logout(request, response, authentication);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return new ResetPasswordResponse("Password reset successfully. Please log in.");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ForgotPasswordResponse(String message, String resetLink) {}
    public record TokenStatusResponse(boolean valid) {}
    public record ResetPasswordResponse(String message) {}
}
