package com.lowcortisol.backend.auth;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class BootstrapAdmin implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final String username;
    private final String email;
    private final String password;

    public BootstrapAdmin(UserRepository users, PasswordEncoder encoder, Validator validator,
        @Value("${app.bootstrap.admin.username}") String username,
        @Value("${app.bootstrap.admin.email}") String email,
        @Value("${app.bootstrap.admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.validator = validator;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        var errors = validator.validate(new RegisterRequest(username, email, password, null));
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid bootstrap admin settings: " + errors.stream()
                .map(error -> error.getPropertyPath() + ": " + error.getMessage()).sorted().toList());
        }
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            requireAdmin(existing.get());
            return; // Never reset an existing account's password or promote a customer.
        }
        try {
            users.create(username, email, encoder.encode(password), null, UserRole.ADMIN);
        } catch (DuplicateKeyException exception) {
            // Another application instance may have created it concurrently.
            requireAdmin(users.findByEmail(email).orElseThrow(() -> exception));
        }
    }

    private void requireAdmin(UserAccount account) {
        if (!UserRole.ADMIN.databaseValue().equals(account.role())) {
            throw new IllegalStateException("Bootstrap admin email belongs to a customer; choose a different email");
        }
    }
}
