package com.lowcortisol.backend.auth;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetRepository {
    private final JdbcTemplate jdbc;

    public PasswordResetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void replaceToken(long userId, String tokenHash) {
        jdbc.update("""
            INSERT INTO password_resets (user_id, token_hash, expires_at)
            VALUES (?, ?, clock_timestamp() + INTERVAL '30 minutes')
            ON CONFLICT (user_id) DO UPDATE SET token_hash = EXCLUDED.token_hash,
                expires_at = EXCLUDED.expires_at, created_at = CURRENT_TIMESTAMP
            """, userId, tokenHash);
    }

    Optional<Long> findValidUserId(String tokenHash) {
        return jdbc.query("SELECT user_id FROM password_resets WHERE token_hash = ? AND expires_at > clock_timestamp()",
            (rs, row) -> rs.getLong("user_id"), tokenHash).stream().findFirst();
    }

    boolean consumeToken(long userId, String tokenHash) {
        return jdbc.update("DELETE FROM password_resets WHERE user_id = ? AND token_hash = ? AND expires_at > clock_timestamp()",
            userId, tokenHash) == 1;
    }

    void deleteTokensForUser(long userId) {
        jdbc.update("DELETE FROM password_resets WHERE user_id = ?", userId);
    }
}
