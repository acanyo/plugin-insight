package com.xhhao.insight.notification;

public interface NotificationChannel {

    String name();

    NotificationResult send(AlertNotification notification) throws Exception;
}
