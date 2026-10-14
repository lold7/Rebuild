package com.lowcortisol.backend.address;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AddressRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<AddressResponse> mapper = (rs, row) -> new AddressResponse(
        rs.getLong("address_id"), rs.getString("label"), rs.getString("full_name"), rs.getString("phone"),
        rs.getString("address_line"), rs.getString("city"), rs.getString("province"), rs.getString("postal_code"),
        rs.getBoolean("is_default"), rs.getObject("created_at", OffsetDateTime.class));

    public AddressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<AddressResponse> list(long userId) {
        return jdbc.query("SELECT * FROM addresses WHERE user_id = ? ORDER BY is_default DESC, created_at DESC, address_id DESC", mapper, userId);
    }

    Optional<AddressResponse> ownedAddress(long userId, long addressId) {
        return jdbc.query("SELECT * FROM addresses WHERE user_id = ? AND address_id = ?", mapper, userId, addressId).stream().findFirst();
    }

    boolean hasDefault(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM addresses WHERE user_id = ? AND is_default = TRUE)",
            Boolean.class, userId));
    }

    AddressResponse add(long userId, AddAddressRequest address, boolean isDefault) {
        return jdbc.queryForObject("""
            INSERT INTO addresses (user_id, label, full_name, phone, address_line, city, province, postal_code, is_default)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *
            """, mapper, userId, defaultValue(address.label(), "Home"), address.fullName().trim(),
            address.phone() == null || address.phone().isBlank() ? null : address.phone().trim(), address.addressLine().trim(),
            defaultValue(address.city(), "Bangkok"), defaultValue(address.province(), "Bangkok"),
            defaultValue(address.postalCode(), "10110"), isDefault);
    }

    void clearDefault(long userId) {
        jdbc.update("UPDATE addresses SET is_default = FALSE WHERE user_id = ? AND is_default = TRUE", userId);
    }

    void setDefault(long userId, long addressId) {
        jdbc.update("UPDATE addresses SET is_default = TRUE WHERE user_id = ? AND address_id = ?", userId, addressId);
    }

    void delete(long userId, long addressId) {
        jdbc.update("DELETE FROM addresses WHERE user_id = ? AND address_id = ?", userId, addressId);
    }

    void promoteNewest(long userId) {
        jdbc.update("""
            UPDATE addresses SET is_default = TRUE WHERE user_id = ? AND address_id = (
                SELECT address_id FROM addresses WHERE user_id = ? ORDER BY created_at DESC, address_id DESC LIMIT 1)
            """, userId, userId);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
