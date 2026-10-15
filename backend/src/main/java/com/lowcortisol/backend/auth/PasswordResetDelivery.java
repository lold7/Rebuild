package com.lowcortisol.backend.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetDelivery {
    private final PasswordResetProperties settings;
    private final ObjectProvider<JavaMailSender> sender;
    private final String smtpHost;

    public PasswordResetDelivery(PasswordResetProperties settings, ObjectProvider<JavaMailSender> sender,
        @Value("${spring.mail.host:}") String smtpHost) {
        this.settings = settings;
        this.sender = sender;
        this.smtpHost = smtpHost;
    }

    public void requireAvailable() {
        if (settings.delivery() == PasswordResetProperties.Delivery.DISABLED
            || (settings.delivery() == PasswordResetProperties.Delivery.SMTP
                && (smtpHost.isBlank() || sender.getIfAvailable() == null))) {
            throw new PasswordResetDeliveryException();
        }
    }

    // A link is returned to the client only in the explicitly enabled local demo mode.
    public String deliver(String email, String token) {
        requireAvailable();
        String baseUrl = settings.linkBaseUrl().toString();
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') end--;
        String link = baseUrl.substring(0, end) + "/" + token;
        if (settings.delivery() == PasswordResetProperties.Delivery.DEMO) {
            return link;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(email);
        message.setSubject("Reset your bookstore password");
        message.setText("To reset your password, open this link:\n" + link
            + "\n\nThis link expires in 30 minutes and can be used once."
            + "\nIf you did not request a password reset, you can ignore this email.");
        try {
            sender.getObject().send(message);
        } catch (MailException exception) {
            // Do not expose transport details or recipient information through the API.
            throw new PasswordResetDeliveryException();
        }
        return null;
    }
}
