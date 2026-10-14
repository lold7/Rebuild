package com.lowcortisol.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAccountRepository {
    private final JdbcTemplate jdbc;

    public CustomerAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long customerId(String email, boolean lock) {
        return jdbc.query("SELECT user_id FROM users WHERE email = ? AND role = 'customer'" + (lock ? " FOR UPDATE" : ""),
            (rs, row) -> rs.getLong("user_id"), email).stream().findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Customer does not exist"));
    }
}
