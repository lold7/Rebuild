package com.lowcortisol.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountSecurityRepository {
    private final JdbcTemplate jdbc;

    public AccountSecurityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void revokeLoginState(String email) {
        jdbc.update("DELETE FROM persistent_logins WHERE username = ?", email);
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", email);
    }
}
