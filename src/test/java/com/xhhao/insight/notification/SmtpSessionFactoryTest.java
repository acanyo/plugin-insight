package com.xhhao.insight.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.config.NotificationSettings;
import org.junit.jupiter.api.Test;

class SmtpSessionFactoryTest {

    private final SmtpSessionFactory factory = new SmtpSessionFactory();

    @Test
    void shouldRequireStartTlsInsteadOfPermittingPlaintextFallback() {
        NotificationSettings settings = settings(587, true);

        var properties = factory.create(settings).getProperties();

        assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.ssl.enable")).isEqualTo("false");
    }

    @Test
    void shouldUseImplicitTlsForPort465() {
        NotificationSettings settings = settings(465, true);

        var properties = factory.create(settings).getProperties();

        assertThat(properties.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("false");
    }

    @Test
    void shouldRejectAuthenticatedPlaintextSmtp() {
        NotificationSettings settings = settings(25, false);

        assertThatThrownBy(() -> factory.create(settings))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires SMTPS or mandatory STARTTLS");
    }

    private static NotificationSettings settings(int port, boolean startTls) {
        NotificationSettings settings = new NotificationSettings();
        settings.setSmtpHost("smtp.example.com");
        settings.setSmtpPort(port);
        settings.setSmtpStartTls(startTls);
        settings.setSmtpUsername("insight");
        settings.setSmtpPassword("secret");
        return settings;
    }
}
