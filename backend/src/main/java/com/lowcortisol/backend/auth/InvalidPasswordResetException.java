package com.lowcortisol.backend.auth;

public class InvalidPasswordResetException extends RuntimeException {
    public InvalidPasswordResetException() {
        super("This reset link is invalid or has expired. Please request a new one.");
    }
}
