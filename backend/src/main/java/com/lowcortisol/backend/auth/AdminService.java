package com.lowcortisol.backend.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse register(AdminRegisterRequest request) {
        return users.create(request.username(), request.email(), encoder.encode(request.password()), request.phone(), UserRole.ADMIN).response();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse currentAdmin(String email) {
        return find(email).response();
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        UserAccount admin = find(email);
        return User.withUsername(admin.email()).password(admin.passwordHash()).roles("ADMIN").build();
    }

    private UserAccount find(String email) {
        return users.findByEmailAndRole(email, UserRole.ADMIN)
            .orElseThrow(() -> new UsernameNotFoundException("Admin does not exist"));
    }
}
