package com.lowcortisol.backend.api;

import com.lowcortisol.backend.auth.InvalidPasswordResetException;
import com.lowcortisol.backend.auth.PasswordResetDeliveryException;
import com.lowcortisol.backend.auth.IncorrectCurrentPasswordException;
import com.lowcortisol.backend.catalog.CatalogNotFoundException;
import com.lowcortisol.backend.catalog.InvalidCatalogRequestException;
import com.lowcortisol.backend.wishlist.InvalidWishlistRequestException;
import com.lowcortisol.backend.address.AddressNotFoundException;
import com.lowcortisol.backend.address.InvalidAddressRequestException;
import com.lowcortisol.backend.cart.CartException;
import com.lowcortisol.backend.payment.PaymentMethodException;
import com.lowcortisol.backend.order.OrderException;
import com.lowcortisol.backend.adminproduct.AdminProductException;
import com.lowcortisol.backend.admincategory.AdminCategoryException;
import com.lowcortisol.backend.adminorder.AdminOrderException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AdminOrderException.class)
    ResponseEntity<ApiError> adminOrderError(AdminOrderException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(AdminCategoryException.class)
    ResponseEntity<ApiError> adminCategoryError(AdminCategoryException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ApiError("PRODUCT_IMAGE_TOO_LARGE", "Images must be at most 5 MiB"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingUpload() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PRODUCT_IMAGE", "A file upload is required"));
    }

    @ExceptionHandler(AdminProductException.class)
    ResponseEntity<ApiError> adminProductError(AdminProductException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(OrderException.class)
    ResponseEntity<ApiError> orderError(OrderException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(PaymentMethodException.class)
    ResponseEntity<ApiError> paymentMethodError(PaymentMethodException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(AddressNotFoundException.class)
    ResponseEntity<ApiError> addressNotFound(AddressNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("ADDRESS_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InvalidAddressRequestException.class)
    ResponseEntity<ApiError> invalidAddress(InvalidAddressRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_ADDRESS_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(CartException.class)
    ResponseEntity<ApiError> cartError(CartException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(InvalidWishlistRequestException.class)
    ResponseEntity<ApiError> invalidWishlistRequest(InvalidWishlistRequestException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_WISHLIST_REQUEST", exception.getMessage()));
    }

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

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("DATABASE_ERROR", "Unable to complete the request. Please try again."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authentication() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiError("INVALID_CREDENTIALS", "Invalid email or password"));
    }
}
