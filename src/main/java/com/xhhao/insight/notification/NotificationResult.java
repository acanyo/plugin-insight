package com.xhhao.insight.notification;

public record NotificationResult(String channel, boolean sent, String message) {

    public static NotificationResult sent(String channel) {
        return new NotificationResult(channel, true, "发送成功");
    }

    public static NotificationResult sent(String channel, String message) {
        return new NotificationResult(channel, true, message);
    }

    public static NotificationResult skipped(String channel, String message) {
        return new NotificationResult(channel, false, message);
    }
}
