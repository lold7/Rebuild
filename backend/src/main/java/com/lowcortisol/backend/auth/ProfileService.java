package com.lowcortisol.backend.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final AccountSecurityRepository security;
    private final PasswordEncoder encoder;

    public ProfileService(UserRepository users, PasswordResetRepository resets,
        AccountSecurityRepository security, PasswordEncoder encoder) {
        this.users = users;
        this.resets = resets;
        this.security = security;
        this.encoder = encoder;
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') && #email == authentication.name")
    public UserResponse currentProfile(String email) {
        return users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Account does not exist")).response();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') && #email == authentication.name")
    public ProfileUpdateResponse updateProfile(String email, ProfileUpdateRequest request) {
        UserAccount user = lockAccount(email);
        boolean emailChanged = !user.email().equals(UserRepository.normalizeEmail(request.email()));
        if (emailChanged) {
            // Remove foreign-key references to the old email before updating it.
            // A conflicting email rolls back the profile update and all revocations.
            security.revokeLoginState(user.email());
            resets.deleteTokensForUser(user.userId());
        }
        UserAccount updated = users.updateProfile(user.userId(), request.username(), request.email(), request.phone());
        return new ProfileUpdateResponse(updated.response(), emailChanged);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN') && #email == authentication.name")
    public void changePassword(String email, ChangePasswordRequest request) {
        UserAccount user = lockAccount(email);
        if (!encoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new IncorrectCurrentPasswordException();
        }
        users.updatePassword(user.userId(), encoder.encode(request.newPassword()));
        resets.deleteTokensForUser(user.userId());
        security.revokeLoginState(user.email());
    }

    private UserAccount lockAccount(String email) {
        return users.lockByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Account does not exist"));
    }
}
