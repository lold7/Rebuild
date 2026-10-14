package com.lowcortisol.backend.auth;

public class IncorrectCurrentPasswordException extends RuntimeException {
    public IncorrectCurrentPasswordException() {
        super("Incorrect current password.");
    }
}
