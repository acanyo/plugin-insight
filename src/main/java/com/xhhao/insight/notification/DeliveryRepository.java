package com.xhhao.insight.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {
    private final SqliteConnectionManager database;
    private final HistoryWriteGate gate;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public DeliveryRepository(SqliteConnectionManager database, HistoryWriteGate gate) {
        this.database = database; this.gate = gate;
    }

    public Delivery enqueue(String channel, AlertNotification notification, long now) {
        if (!gate.allowsWrite()) { throw new IllegalStateException("通知历史存储不可用"); }
        String payload;
        try { payload = json.writeValueAsString(notification); }
        catch (Exception e) { throw new IllegalArgumentException("通知编码失败", e); }
        var delivery = new Delivery(UUID.randomUUID().toString(), channel, payload,
            "pending", 0, now, now, now, "等待投递");
        database.write(connection -> {
            try (var supersede = connection.prepareStatement("""
                UPDATE notification_delivery SET state='superseded',updated_at_ms=?,message='已由较新的告警状态取代'
                WHERE state='pending' AND channel=? AND json_extract(payload,'$.incidentId')=?
                """)) {
                supersede.setLong(1, now); supersede.setString(2, channel);
                supersede.setString(3, notification.incidentId()); supersede.executeUpdate();
            }
            try (var count = connection.createStatement(); var rows = count.executeQuery(
                "SELECT COUNT(*) FROM notification_delivery WHERE state='pending'")) {
                if (rows.next() && rows.getInt(1) >= 2000) { throw new IllegalStateException("通知队列已满"); }
            }
            try (var statement = connection.prepareStatement(
                "INSERT INTO notification_delivery VALUES(?,?,?,?,?,?,?,?,?)")) {
                statement.setString(1, delivery.id()); statement.setString(2, channel);
                statement.setString(3, delivery.payload()); statement.setString(4, "pending");
                statement.setInt(5, 0); statement.setLong(6, now); statement.setLong(7, now);
                statement.setLong(8, now); statement.setString(9, delivery.message()); statement.executeUpdate();
            }
            return null;
        });
        return delivery;
    }

    public List<Delivery> list(boolean dueOnly, long now, int limit) {
        return database.read(connection -> {
            var result = new ArrayList<Delivery>();
            String filter = dueOnly ? "WHERE state='pending' AND next_at_ms <= ? ORDER BY created_at_ms,id"
                : "WHERE updated_at_ms <= ? ORDER BY updated_at_ms DESC,id DESC";
            try (var statement = connection.prepareStatement("SELECT * FROM notification_delivery " + filter + " LIMIT ?")) {
                statement.setLong(1, now); statement.setInt(2, Math.clamp(limit, 1, 200));
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) { result.add(new Delivery(rows.getString("id"), rows.getString("channel"),
                        rows.getString("payload"), rows.getString("state"), rows.getInt("attempts"),
                        rows.getLong("next_at_ms"), rows.getLong("created_at_ms"), rows.getLong("updated_at_ms"), rows.getString("message"))); }
                }
            }
            return List.copyOf(result);
        });
    }

    public AlertNotification notification(Delivery delivery) {
        try { return json.readValue(delivery.payload(), AlertNotification.class); }
        catch (Exception e) { throw new IllegalArgumentException("通知记录无法解码", e); }
    }

    public void acknowledge(String incidentId) {
        database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                UPDATE notification_delivery SET state='superseded',updated_at_ms=?,message='告警已确认，取消待发提醒'
                WHERE state='pending' AND json_extract(payload,'$.incidentId')=? AND json_extract(payload,'$.recovered')=0
                """)) {
                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, incidentId); statement.executeUpdate();
            }
            return null;
        });
    }

    public void finish(Delivery delivery, String state, int attempts, long nextAt, String message) {
        database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                UPDATE notification_delivery SET state=?,attempts=?,next_at_ms=?,updated_at_ms=?,message=? WHERE id=?
                """)) {
                statement.setString(1, state); statement.setInt(2, attempts); statement.setLong(3, nextAt);
                statement.setLong(4, System.currentTimeMillis()); statement.setString(5, message);
                statement.setString(6, delivery.id()); statement.executeUpdate();
            }
            return null;
        });
    }

    public record Delivery(String id, String channel, String payload, String state, int attempts,
                           long nextAtMs, long createdAtMs, long updatedAtMs, String message) {}
}
