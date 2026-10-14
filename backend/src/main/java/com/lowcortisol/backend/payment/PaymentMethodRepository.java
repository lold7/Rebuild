package com.lowcortisol.backend.payment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentMethodRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<PaymentMethod> mapper = (rs, row) -> new PaymentMethod(
        rs.getLong("payment_method_id"), rs.getString("type"), rs.getString("brand"), rs.getString("cardholder_name"),
        rs.getString("last4"), rs.getInt("expiry_month"), rs.getInt("expiry_year"), rs.getString("currency"),
        rs.getBoolean("is_default"), rs.getObject("created_at", OffsetDateTime.class));

    public PaymentMethodRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<PaymentMethod> list(long userId) {
        return jdbc.query("""
            SELECT * FROM payment_methods WHERE user_id = ?
            ORDER BY is_default DESC, created_at DESC, payment_method_id DESC
            """, mapper, userId);
    }

    Optional<PaymentMethod> owned(long userId, long id) {
        return jdbc.query("SELECT * FROM payment_methods WHERE user_id = ? AND payment_method_id = ?",
            mapper, userId, id).stream().findFirst();
    }

    PaymentMethod add(long userId, AddPaymentMethodRequest method, String type, String brand, String currency, boolean isDefault) {
        return jdbc.queryForObject("""
            INSERT INTO payment_methods (user_id, type, brand, cardholder_name, last4, expiry_month, expiry_year, currency, is_default)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *
            """, mapper, userId, type, brand, method.cardholderName().trim(), method.last4(),
            method.expiryMonth(), method.expiryYear(), currency, isDefault);
    }

    void clearDefault(long userId) {
        jdbc.update("UPDATE payment_methods SET is_default = FALSE WHERE user_id = ? AND is_default = TRUE", userId);
    }

    void setDefault(long userId, long id) {
        jdbc.update("UPDATE payment_methods SET is_default = TRUE WHERE user_id = ? AND payment_method_id = ?", userId, id);
    }

    void delete(long userId, long id) {
        jdbc.update("DELETE FROM payment_methods WHERE user_id = ? AND payment_method_id = ?", userId, id);
    }
}
