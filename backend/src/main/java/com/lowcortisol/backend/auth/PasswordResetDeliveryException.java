package com.lowcortisol.backend.auth;

public class PasswordResetDeliveryException extends RuntimeException {
    public PasswordResetDeliveryException() {
        super("Password reset delivery is unavailable. Please try again later.");
    }
}
