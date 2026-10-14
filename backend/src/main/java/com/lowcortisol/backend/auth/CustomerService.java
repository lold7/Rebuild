package com.lowcortisol.backend.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CustomerService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public CustomerService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public UserResponse register(RegisterRequest request) {
        return users.create(request.username(), request.email(), encoder.encode(request.password()), request.phone(), UserRole.CUSTOMER).response();
    }

    public UserResponse currentCustomer(String email) {
        return find(email).response();
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        UserAccount customer = find(email);
        return User.withUsername(customer.email()).password(customer.passwordHash()).roles("CUSTOMER").build();
    }

    private UserAccount find(String email) {
        return users.findByEmailAndRole(email, UserRole.CUSTOMER)
            .orElseThrow(() -> new UsernameNotFoundException("Customer does not exist"));
    }
}
