package com.xhhao.insight.notification;

import com.xhhao.insight.config.InsightSettingsService;
import jakarta.mail.Message;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final InsightSettingsService settingsService;
    private final SmtpSessionFactory sessionFactory;

    public EmailNotificationChannel(
        InsightSettingsService settingsService,
        SmtpSessionFactory sessionFactory
    ) {
        this.settingsService = settingsService;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public NotificationResult send(AlertNotification notification) throws Exception {
        var settings = settingsService.notifications();
        if (!settings.isEmailEnabled()) {
            return NotificationResult.skipped(name(), "邮件通知未启用");
        }
        if (blank(settings.getSmtpHost()) || blank(settings.getSmtpFrom())
            || settings.getRecipients().isEmpty()) {
            return NotificationResult.skipped(name(), "邮件配置不完整");
        }
        MimeMessage message = new MimeMessage(sessionFactory.create(settings));
        message.setFrom(new InternetAddress(settings.getSmtpFrom()));
        for (String recipient : settings.getRecipients()) {
            if (!blank(recipient)) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient.trim()));
            }
        }
        message.setSubject("[Halo Insight] " + notification.title(), StandardCharsets.UTF_8.name());
        message.setText(emailBody(notification), StandardCharsets.UTF_8.name());
        Transport.send(message);
        return NotificationResult.sent(name());
    }

    private static String emailBody(AlertNotification notification) {
        return notification.message() + "\n\n"
            + "级别: " + notification.severity() + "\n"
            + "指标: " + notification.metric() + "\n"
            + "当前值: " + notification.value() + "\n"
            + "阈值: " + notification.threshold() + "\n"
            + "时间: " + notification.occurredAt() + "\n"
            + "状态: " + (notification.recovered() ? "已恢复" : "告警中") + "\n";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
