package com.lowcortisol.backend.auth;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordResetDeliveryTest {
    @Test
    void smtpDeliversResetLinkWithoutReturningItToApiClient() {
        JavaMailSender sender = mock(JavaMailSender.class);
        PasswordResetDelivery delivery = delivery(PasswordResetProperties.Delivery.SMTP, sender, "smtp.example.com");
        assertThat(delivery.deliver("alice@example.com", "a".repeat(64))).isNull();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("alice@example.com");
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@example.com");
        assertThat(captor.getValue().getText()).contains("https://bookstore.example/reset-password/" + "a".repeat(64), "30 minutes");
    }

    @Test
    void demoLinksRemoveOnlyTrailingSlashesWithoutBacktracking() {
        JavaMailSender sender = mock(JavaMailSender.class);
        String base = "https://bookstore.example/reset-password";
        for (String suffix : new String[] {"", "/", "///", "/".repeat(20000)}) {
            var settings = new PasswordResetProperties(PasswordResetProperties.Delivery.DEMO,
                URI.create(base + suffix), "noreply@example.com");
            var beans = new StaticListableBeanFactory(Map.of("sender", sender));
            var delivery = new PasswordResetDelivery(settings, beans.getBeanProvider(JavaMailSender.class), "");
            assertThat(delivery.deliver("alice@example.com", "token")).isEqualTo(base + "/token");
        }
        String internalSlashes = base + "/".repeat(20000) + "path";
        var settings = new PasswordResetProperties(PasswordResetProperties.Delivery.DEMO,
            URI.create(internalSlashes), "noreply@example.com");
        var beans = new StaticListableBeanFactory(Map.of("sender", sender));
        var delivery = new PasswordResetDelivery(settings, beans.getBeanProvider(JavaMailSender.class), "");
        assertThat(delivery.deliver("alice@example.com", "token")).isEqualTo(internalSlashes + "/token");
        verifyNoInteractions(sender);
    }

    @Test
    void smtpFailureReturnsGenericDeliveryError() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("private transport details")).when(sender).send(any(SimpleMailMessage.class));
        PasswordResetDelivery delivery = delivery(PasswordResetProperties.Delivery.SMTP, sender, "smtp.example.com");
        String token = "a".repeat(64);
        assertThatThrownBy(() -> delivery.deliver("alice@example.com", token))
            .isInstanceOf(PasswordResetDeliveryException.class).hasMessageNotContaining("private transport details");
    }

    @Test
    void disabledOrUnconfiguredDeliveryCannotIssueLinks() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var disabledDelivery = delivery(PasswordResetProperties.Delivery.DISABLED, sender, "");
        assertThatThrownBy(disabledDelivery::requireAvailable)
            .isInstanceOf(PasswordResetDeliveryException.class);
        var unconfiguredDelivery = delivery(PasswordResetProperties.Delivery.SMTP, sender, "");
        assertThatThrownBy(unconfiguredDelivery::requireAvailable)
            .isInstanceOf(PasswordResetDeliveryException.class);
        verifyNoInteractions(sender);
    }

    private PasswordResetDelivery delivery(PasswordResetProperties.Delivery mode, JavaMailSender sender, String host) {
        var settings = new PasswordResetProperties(mode, URI.create("https://bookstore.example/reset-password/"), "noreply@example.com");
        var beans = new StaticListableBeanFactory(Map.of("sender", sender));
        return new PasswordResetDelivery(settings, beans.getBeanProvider(JavaMailSender.class), host);
    }
}
