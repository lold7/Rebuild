package com.lowcortisol.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(
    @NotNull Delivery delivery,
    @NotNull URI linkBaseUrl,
    @NotBlank @Email String from
) {
    public enum Delivery { DISABLED, DEMO, SMTP }

    public PasswordResetProperties {
        if (linkBaseUrl != null && (!("http".equalsIgnoreCase(linkBaseUrl.getScheme())
            || "https".equalsIgnoreCase(linkBaseUrl.getScheme())) || linkBaseUrl.getHost() == null
            || linkBaseUrl.getQuery() != null || linkBaseUrl.getFragment() != null || linkBaseUrl.getUserInfo() != null)) {
            throw new IllegalArgumentException("Password reset link-base-url must be an absolute HTTP(S) URL without credentials, query or fragment");
        }
    }
}
