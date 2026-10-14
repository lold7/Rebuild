package com.lowcortisol.backend.api;

import com.lowcortisol.backend.auth.InvalidPasswordResetException;
import com.lowcortisol.backend.auth.PasswordResetDeliveryException;
import com.lowcortisol.backend.auth.IncorrectCurrentPasswordException;
import com.lowcortisol.backend.catalog.CatalogNotFoundException;
import com.lowcortisol.backend.catalog.InvalidCatalogRequestException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CatalogNotFoundException.class)
    ResponseEntity<ApiError> catalogNotFound(CatalogNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("CATALOG_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCatalogRequestException.class)
    ResponseEntity<ApiError> invalidCatalogRequest(InvalidCatalogRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_CATALOG_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    ResponseEntity<ApiError> incorrectPassword(IncorrectCurrentPasswordException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INCORRECT_CURRENT_PASSWORD", exception.getMessage()));
    }

    @ExceptionHandler(InvalidPasswordResetException.class)
    ResponseEntity<ApiError> invalidReset(InvalidPasswordResetException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_RESET_TOKEN", exception.getMessage()));
    }

    @ExceptionHandler(PasswordResetDeliveryException.class)
    ResponseEntity<ApiError> unavailableDelivery(PasswordResetDeliveryException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError("RESET_DELIVERY_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Invalid request fields", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_JSON", "Invalid JSON request"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("EMAIL_IN_USE", "Email is already registered"));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authentication() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiError("INVALID_CREDENTIALS", "Invalid email or password"));
    }
}
