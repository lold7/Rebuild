package com.lowcortisol.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminService admins;
    private final AuthenticationManager authenticationManager;
    private final AuthenticationSessionService sessions;

    public AdminAuthController(AdminService admins,
        @Qualifier("adminAuthenticationManager") AuthenticationManager authenticationManager,
        AuthenticationSessionService sessions) {
        this.admins = admins;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody AdminLoginRequest credentials,
        HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                UserRepository.normalizeEmail(credentials.email()), credentials.password()));
        // Admin logins use a browser-session cookie and clear customer remembered-login state.
        sessions.establish(authentication, false, request, response);
        return admins.currentAdmin(authentication.getName());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody AdminRegisterRequest request) {
        return ResponseEntity.status(201).body(admins.register(request));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return admins.currentAdmin(authentication.getName());
    }
}
