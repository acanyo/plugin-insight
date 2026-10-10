package com.xhhao.insight.config;

import java.util.ArrayList;
import java.util.List;

public class NotificationSettings {

    private List<String> haloNotificationRecipients = new ArrayList<>();
    private boolean webhookEnabled;
    private String webhookUrl = "";
    private String webhookSecret = "";
    private boolean webhookAllowPrivateNetwork;

    public List<String> getHaloNotificationRecipients() {
        return haloNotificationRecipients;
    }

    public void setHaloNotificationRecipients(List<String> haloNotificationRecipients) {
        this.haloNotificationRecipients = haloNotificationRecipients == null
            ? new ArrayList<>()
            : new ArrayList<>(haloNotificationRecipients);
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public void setWebhookEnabled(boolean webhookEnabled) {
        this.webhookEnabled = webhookEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isWebhookAllowPrivateNetwork() {
        return webhookAllowPrivateNetwork;
    }

    public void setWebhookAllowPrivateNetwork(boolean webhookAllowPrivateNetwork) {
        this.webhookAllowPrivateNetwork = webhookAllowPrivateNetwork;
    }

}
