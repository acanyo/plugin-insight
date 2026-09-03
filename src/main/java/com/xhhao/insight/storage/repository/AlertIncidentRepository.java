package com.xhhao.insight.storage.repository;

import com.xhhao.insight.storage.database.SqliteConnectionManager;
import com.xhhao.insight.storage.model.AlertIncident;
import com.xhhao.insight.storage.model.AlertIncidentCursor;
import com.xhhao.insight.storage.model.AlertIncidentPage;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AlertIncidentRepository {

    private static final String INSERT_SQL = """
        INSERT INTO alert_incident(
            id, rule_id, status, severity, title, summary, fingerprint,
            triggered_at_ms, updated_at_ms, resolved_at_ms, occurrence_count,
            last_value, threshold_value, details_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String UPDATE_SQL = """
        UPDATE alert_incident SET
            rule_id=?, status=?, severity=?, title=?, summary=?, fingerprint=?,
            triggered_at_ms=?, updated_at_ms=?, resolved_at_ms=?, occurrence_count=?,
            last_value=?, threshold_value=?, details_json=?
        WHERE id=?
        """;

    private final SqliteConnectionManager connectionManager;

    public AlertIncidentRepository(SqliteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public void insert(AlertIncident incident) {
        connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                bindInsert(statement, incident);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public boolean update(AlertIncident incident) {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
                bindUpdate(statement, incident);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void save(AlertIncident incident) {
        connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                INSERT_SQL + """
                    ON CONFLICT(id) DO UPDATE SET
                        rule_id=excluded.rule_id,
                        status=excluded.status,
                        severity=excluded.severity,
                        title=excluded.title,
                        summary=excluded.summary,
                        fingerprint=excluded.fingerprint,
                        triggered_at_ms=excluded.triggered_at_ms,
                        updated_at_ms=excluded.updated_at_ms,
                        resolved_at_ms=excluded.resolved_at_ms,
                        occurrence_count=excluded.occurrence_count,
                        last_value=excluded.last_value,
                        threshold_value=excluded.threshold_value,
                        details_json=excluded.details_json
                    """)) {
                bindInsert(statement, incident);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public Optional<AlertIncident> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return connectionManager.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM alert_incident WHERE id=?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            }
        });
    }

    public List<AlertIncident> findByStatus(String status, int limit) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return connectionManager.read(connection -> {
            List<AlertIncident> incidents = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM alert_incident
                WHERE status=?
                ORDER BY updated_at_ms DESC, id DESC
                LIMIT ?
                """)) {
                statement.setString(1, status);
                statement.setInt(2, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        incidents.add(map(resultSet));
                    }
                }
            }
            return incidents;
        });
    }

    /** Reads a keyset page ordered by updated time descending, optionally filtered by status. */
    public AlertIncidentPage findPage(String status, AlertIncidentCursor before, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return connectionManager.read(connection -> {
            List<AlertIncident> incidents = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM alert_incident WHERE 1=1");
            if (status != null && !status.isBlank()) {
                sql.append(" AND status=?");
            }
            if (before != null) {
                sql.append(" AND (updated_at_ms < ? OR (updated_at_ms = ? AND id < ?))");
            }
            sql.append(" ORDER BY updated_at_ms DESC, id DESC LIMIT ?");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                if (status != null && !status.isBlank()) {
                    statement.setString(index++, status);
                }
                if (before != null) {
                    statement.setLong(index++, before.updatedAtMs());
                    statement.setLong(index++, before.updatedAtMs());
                    statement.setString(index++, before.id());
                }
                statement.setInt(index, limit + 1);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        incidents.add(map(resultSet));
                    }
                }
            }
            boolean hasMore = incidents.size() > limit;
            if (hasMore) {
                incidents.subList(limit, incidents.size()).clear();
            }
            AlertIncidentCursor nextCursor = null;
            if (hasMore && !incidents.isEmpty()) {
                AlertIncident last = incidents.get(incidents.size() - 1);
                nextCursor = new AlertIncidentCursor(last.updatedAtMs(), last.id());
            }
            return new AlertIncidentPage(incidents, nextCursor);
        });
    }

    public List<AlertIncident> findRange(
        long fromInclusiveMs,
        long toExclusiveMs,
        int limit
    ) {
        QueryRangeValidator.validate(fromInclusiveMs, toExclusiveMs, limit);
        return connectionManager.read(connection -> {
            List<AlertIncident> incidents = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM alert_incident
                WHERE triggered_at_ms >= ? AND triggered_at_ms < ?
                ORDER BY triggered_at_ms ASC, id ASC
                LIMIT ?
                """)) {
                statement.setLong(1, fromInclusiveMs);
                statement.setLong(2, toExclusiveMs);
                statement.setInt(3, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        incidents.add(map(resultSet));
                    }
                }
            }
            return incidents;
        });
    }

    public boolean deleteById(String id) {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM alert_incident WHERE id=?")) {
                statement.setString(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public int deleteAll() {
        return connectionManager.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM alert_incident")) {
                return statement.executeUpdate();
            }
        });
    }

    private static void bindInsert(PreparedStatement statement, AlertIncident incident)
        throws SQLException {
        statement.setString(1, incident.id());
        statement.setString(2, incident.ruleId());
        statement.setString(3, incident.status());
        statement.setString(4, incident.severity());
        statement.setString(5, incident.title());
        statement.setString(6, incident.summary());
        statement.setString(7, incident.fingerprint());
        statement.setLong(8, incident.triggeredAtMs());
        statement.setLong(9, incident.updatedAtMs());
        setNullableLong(statement, 10, incident.resolvedAtMs());
        statement.setLong(11, incident.occurrenceCount());
        setNullableDouble(statement, 12, incident.lastValue());
        setNullableDouble(statement, 13, incident.thresholdValue());
        statement.setString(14, incident.detailsJson());
    }

    private static void bindUpdate(PreparedStatement statement, AlertIncident incident)
        throws SQLException {
        statement.setString(1, incident.ruleId());
        statement.setString(2, incident.status());
        statement.setString(3, incident.severity());
        statement.setString(4, incident.title());
        statement.setString(5, incident.summary());
        statement.setString(6, incident.fingerprint());
        statement.setLong(7, incident.triggeredAtMs());
        statement.setLong(8, incident.updatedAtMs());
        setNullableLong(statement, 9, incident.resolvedAtMs());
        statement.setLong(10, incident.occurrenceCount());
        setNullableDouble(statement, 11, incident.lastValue());
        setNullableDouble(statement, 12, incident.thresholdValue());
        statement.setString(13, incident.detailsJson());
        statement.setString(14, incident.id());
    }

    private static AlertIncident map(ResultSet resultSet) throws SQLException {
        return new AlertIncident(
            resultSet.getString("id"),
            resultSet.getString("rule_id"),
            resultSet.getString("status"),
            resultSet.getString("severity"),
            resultSet.getString("title"),
            resultSet.getString("summary"),
            resultSet.getString("fingerprint"),
            resultSet.getLong("triggered_at_ms"),
            resultSet.getLong("updated_at_ms"),
            nullableLong(resultSet, "resolved_at_ms"),
            resultSet.getLong("occurrence_count"),
            nullableDouble(resultSet, "last_value"),
            nullableDouble(resultSet, "threshold_value"),
            resultSet.getString("details_json")
        );
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
        throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value)
        throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}
