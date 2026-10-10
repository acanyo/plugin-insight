package com.xhhao.insight.notification;

import com.xhhao.insight.config.InsightSettingsService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookNotificationChannel implements NotificationChannel {

    private final InsightSettingsService settingsService;
    private final WebhookDestinationPolicy destinationPolicy;
    private final HttpClient httpClient;

    public WebhookNotificationChannel(
        InsightSettingsService settingsService,
        WebhookDestinationPolicy destinationPolicy
    ) {
        this.settingsService = settingsService;
        this.destinationPolicy = destinationPolicy;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public NotificationResult send(AlertNotification notification) throws Exception {
        var settings = settingsService.notifications();
        if (!settings.isWebhookEnabled() || blank(settings.getWebhookUrl())) {
            return NotificationResult.skipped(name(), "Webhook 未启用");
        }
        String body = toJson(notification);
        URI destination = destinationPolicy.validate(
            settings.getWebhookUrl(), settings.isWebhookAllowPrivateNetwork());
        HttpRequest.Builder request = HttpRequest.newBuilder(destination)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Halo-Insight/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!blank(settings.getWebhookSecret())) {
            request.header("X-Insight-Signature", "sha256=" + hmac(body, settings.getWebhookSecret()));
        }
        HttpResponse<Void> response = httpClient.send(request.build(),
            HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Webhook returned HTTP " + response.statusCode());
        }
        return NotificationResult.sent(name());
    }

    private static String toJson(AlertNotification value) {
        return "{" +
            "\"incidentId\":\"" + escape(value.incidentId()) + "\"," +
            "\"title\":\"" + escape(value.title()) + "\"," +
            "\"severity\":\"" + escape(value.severity()) + "\"," +
            "\"message\":\"" + escape(value.message()) + "\"," +
            "\"metric\":\"" + escape(value.metric()) + "\"," +
            "\"value\":" + value.value() + "," +
            "\"threshold\":" + value.threshold() + "," +
            "\"occurredAt\":\"" + NotificationTimeFormatter.format(value.occurredAt()) + "\"," +
            "\"recovered\":" + value.recovered() +
            "}";
    }

    private static String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
