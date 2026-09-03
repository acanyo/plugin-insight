package com.xhhao.insight.notification;

import com.xhhao.insight.config.NotificationSettings;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import java.util.Properties;
import org.springframework.stereotype.Component;

/**
 * Creates SMTP sessions with an explicit transport-security policy.
 *
 * <p>Port 465 uses implicit TLS (SMTPS). Other secure SMTP connections use mandatory STARTTLS,
 * which prevents an active server or proxy from downgrading authenticated mail to plaintext.</p>
 */
@Component
public class SmtpSessionFactory {

    static final int SMTPS_PORT = 465;

    public Session create(NotificationSettings settings) {
        boolean authenticate = !blank(settings.getSmtpUsername());
        boolean implicitTls = settings.getSmtpPort() == SMTPS_PORT;
        boolean startTls = !implicitTls && settings.isSmtpStartTls();
        if (authenticate && !implicitTls && !startTls) {
            throw new IllegalArgumentException(
                "SMTP authentication requires SMTPS or mandatory STARTTLS");
        }

        Properties properties = properties(settings, authenticate, implicitTls, startTls);
        Authenticator authenticator = authenticate ? new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    settings.getSmtpUsername(), settings.getSmtpPassword());
            }
        } : null;
        return Session.getInstance(properties, authenticator);
    }

    static Properties properties(
        NotificationSettings settings,
        boolean authenticate,
        boolean implicitTls,
        boolean startTls
    ) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", settings.getSmtpHost());
        properties.put("mail.smtp.port", Integer.toString(settings.getSmtpPort()));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        properties.put("mail.smtp.auth", Boolean.toString(authenticate));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(implicitTls));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.put("mail.smtp.starttls.required", Boolean.toString(startTls));
        if (implicitTls || startTls) {
            properties.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        return properties;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
