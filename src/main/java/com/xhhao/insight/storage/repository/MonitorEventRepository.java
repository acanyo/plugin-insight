package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.MonitorEvent;
import com.xhhao.insight.storage.model.MonitorEventCursor;
import com.xhhao.insight.storage.model.MonitorEventPage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorEventRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO monitor_event(
            id, occurred_at_ms, type, severity, source, title, summary, fingerprint,
            first_seen_at_ms, last_seen_at_ms, occurrence_count, details_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            occurred_at_ms=excluded.occurred_at_ms,
            type=excluded.type,
            severity=excluded.severity,
            source=excluded.source,
            title=excluded.title,
            summary=excluded.summary,
            fingerprint=excluded.fingerprint,
            first_seen_at_ms=excluded.first_seen_at_ms,
            last_seen_at_ms=excluded.last_seen_at_ms,
            occurrence_count=excluded.occurrence_count,
            details_json=excluded.details_json
        """;

    private final SqliteConnectionManager connectionManager;

    public MonitorEventRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void save(MonitorEvent event) {
        saveBatch(List.of(event));
    }

    public void saveBatch(List<MonitorEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                for (MonitorEvent event : events) {
                    bind(statement, event);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    public MonitorEventPage findPage(
        long fromInclusiveMs,
        long toExclusiveMs,
        String type,
        String severity,
        MonitorEventCursor before,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM monitor_event
            WHERE occurred_at_ms >= ? AND occurred_at_ms < ?
            """);
        if (type != null && !type.isBlank()) {
            sql.append(" AND type = ?");
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" AND severity = ?");
        }
        if (before != null) {
            sql.append(" AND (occurred_at_ms < ? OR (occurred_at_ms = ? AND id < ?))");
        }
        sql.append(" ORDER BY occurred_at_ms DESC, id DESC LIMIT ?");

        return connectionManager.read(connection -> {
            List<MonitorEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                statement.setLong(index++, fromInclusiveMs);
                statement.setLong(index++, toExclusiveMs);
                if (type != null && !type.isBlank()) {
                    statement.setString(index++, type);
                }
                if (severity != null && !severity.isBlank()) {
                    statement.setString(index++, severity);
                }
                if (before != null) {
                    statement.setLong(index++, before.occurredAtMs());
                    statement.setLong(index++, before.occurredAtMs());
                    statement.setString(index++, before.id());
                }
                statement.setInt(index, limit + 1);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        events.add(map(resultSet));
                    }
                }
            }
            boolean hasMore = events.size() > limit;
            if (hasMore) {
                events.subList(limit, events.size()).clear();
            }
            MonitorEventCursor nextCursor = null;
            if (hasMore && !events.isEmpty()) {
                MonitorEvent last = events.get(events.size() - 1);
                nextCursor = new MonitorEventCursor(last.occurredAtMs(), last.id());
            }
            return new MonitorEventPage(events, nextCursor);
        });
    }

    public List<MonitorEvent> findRange(long fromInclusiveMs, long toExclusiveMs, int limit) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        return connectionManager.read(connection -> {
            List<MonitorEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM monitor_event
                WHERE occurred_at_ms >= ? AND occurred_at_ms < ?
                ORDER BY occurred_at_ms ASC, id ASC
                LIMIT ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        events.add(map(resultSet));
                    }
                }
            }
            return events;
        });
    }

    public int deleteAll() {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM monitor_event")) {
                return statement.executeUpdate();
            }
        });
    }

    private static void bind(PreparedStatement statement, MonitorEvent event) throws SQLException {
        statement.setString(1, event.id());
        statement.setLong(2, event.occurredAtMs());
        statement.setString(3, event.type());
        statement.setString(4, event.severity());
        statement.setString(5, event.source());
        statement.setString(6, event.title());
        statement.setString(7, event.summary());
        statement.setString(8, event.fingerprint());
        statement.setLong(9, event.firstSeenAtMs());
        statement.setLong(10, event.lastSeenAtMs());
        statement.setLong(11, event.occurrenceCount());
        statement.setString(12, event.detailsJson());
    }

    private static MonitorEvent map(ResultSet resultSet) throws SQLException {
        return new MonitorEvent(
            resultSet.getString("id"),
            resultSet.getLong("occurred_at_ms"),
            resultSet.getString("type"),
            resultSet.getString("severity"),
            resultSet.getString("source"),
            resultSet.getString("title"),
            resultSet.getString("summary"),
            resultSet.getString("fingerprint"),
            resultSet.getLong("first_seen_at_ms"),
            resultSet.getLong("last_seen_at_ms"),
            resultSet.getLong("occurrence_count"),
            resultSet.getString("details_json")
        );
    }
}
