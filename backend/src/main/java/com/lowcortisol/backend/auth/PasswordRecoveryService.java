package com.lowcortisol.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final PasswordResetDelivery delivery;
    private final PasswordEncoder encoder;
    private final AccountSecurityRepository security;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(UserRepository users, PasswordResetRepository resets,
        PasswordResetDelivery delivery, PasswordEncoder encoder, AccountSecurityRepository security) {
        this.users = users;
        this.resets = resets;
        this.delivery = delivery;
        this.encoder = encoder;
        this.security = security;
    }

    @Transactional
    public String requestReset(String email) {
        delivery.requireAvailable();
        var user = users.lockByEmail(email);
        if (user.isEmpty()) return null;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        resets.replaceToken(user.get().userId(), hash(token));
        // Delivery failure rolls back token replacement, preserving the preceding valid link.
        return delivery.deliver(user.get().email(), token);
    }

    public void validateToken(String token) {
        if (resets.findValidUserId(validTokenHash(token)).isEmpty()) throw new InvalidPasswordResetException();
    }

    @Transactional
    public String resetPassword(String token, String newPassword) {
        String tokenHash = validTokenHash(token);
        long userId = resets.findValidUserId(tokenHash).orElseThrow(InvalidPasswordResetException::new);
        // All token changes lock the account first, serializing requests and concurrent resets.
        UserAccount user = users.lockById(userId).orElseThrow(InvalidPasswordResetException::new);
        if (!resets.consumeToken(userId, tokenHash)) throw new InvalidPasswordResetException();
        users.updatePassword(userId, encoder.encode(newPassword));
        security.revokeLoginState(user.email());
        return user.email();
    }

    private String validTokenHash(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw new InvalidPasswordResetException();
        return hash(token);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
