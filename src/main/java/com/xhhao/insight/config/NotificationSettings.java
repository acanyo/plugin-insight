package com.xhhao.insight.config;

import java.util.ArrayList;
import java.util.List;

public class NotificationSettings {

    private boolean haloNotificationEnabled;
    private List<String> haloNotificationRecipients = new ArrayList<>();
    private boolean webhookEnabled;
    private String webhookUrl = "";
    private String webhookSecret = "";
    private boolean webhookAllowPrivateNetwork;
    private boolean emailEnabled;
    private String smtpHost = "";
    private int smtpPort = 587;
    private boolean smtpStartTls = true;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private String smtpFrom = "";
    private List<String> recipients = new ArrayList<>();

    public boolean isHaloNotificationEnabled() {
        return haloNotificationEnabled;
    }

    public void setHaloNotificationEnabled(boolean haloNotificationEnabled) {
        this.haloNotificationEnabled = haloNotificationEnabled;
    }

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

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public boolean isSmtpStartTls() {
        return smtpStartTls;
    }

    public void setSmtpStartTls(boolean smtpStartTls) {
        this.smtpStartTls = smtpStartTls;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public String getSmtpFrom() {
        return smtpFrom;
    }

    public void setSmtpFrom(String smtpFrom) {
        this.smtpFrom = smtpFrom;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients == null ? new ArrayList<>() : new ArrayList<>(recipients);
    }
}
