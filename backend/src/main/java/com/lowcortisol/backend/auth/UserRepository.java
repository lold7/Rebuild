package com.lowcortisol.backend.auth;

import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<UserAccount> mapper = (rs, row) -> new UserAccount(
        rs.getLong("user_id"), rs.getString("username"), rs.getString("email"),
        rs.getString("password_hash"), rs.getString("phone"), rs.getString("role"));

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    Optional<UserAccount> findByEmailAndRole(String email, UserRole role) {
        return jdbc.query("SELECT * FROM users WHERE email = ? AND role = ?",
            mapper, normalizeEmail(email), role.databaseValue()).stream().findFirst();
    }

    Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = ?", mapper, normalizeEmail(email)).stream().findFirst();
    }

    UserAccount create(String username, String email, String passwordHash, String phone, UserRole role) {
        return jdbc.queryForObject("""
            INSERT INTO users (username, email, password_hash, phone, role)
            VALUES (?, ?, ?, ?, ?) RETURNING *
            """, mapper, username.trim(), normalizeEmail(email), passwordHash,
            phone == null || phone.isBlank() ? null : phone.trim(), role.databaseValue());
    }
}
