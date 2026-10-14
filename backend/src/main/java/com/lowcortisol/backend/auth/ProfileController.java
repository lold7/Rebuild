package com.lowcortisol.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {
    private final ProfileService profiles;
    private final AuthenticationSessionService sessions;

    public ProfileController(ProfileService profiles, AuthenticationSessionService sessions) {
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @GetMapping({"/api/account/profile", "/api/admin/profile"})
    public UserResponse profile(Authentication authentication) {
        return profiles.currentProfile(authentication.getName());
    }

    @PostMapping({"/api/account/profile", "/api/admin/profile"})
    public ProfileUpdateResponse update(@Valid @RequestBody ProfileUpdateRequest changes,
        Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        ProfileUpdateResponse result = profiles.updateProfile(authentication.getName(), changes);
        if (result.reauthenticationRequired()) sessions.clear(authentication, request, response);
        return result;
    }

    @PostMapping({"/api/account/password", "/api/admin/password"})
    public PasswordChangedResponse password(@Valid @RequestBody ChangePasswordRequest credentials,
        Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        profiles.changePassword(authentication.getName(), credentials);
        sessions.clear(authentication, request, response);
        return new PasswordChangedResponse("Password changed successfully. Please log in.");
    }

    public record PasswordChangedResponse(String message) {}
}
