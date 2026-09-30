package com.xhhao.insight.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.storage.capacity.HistoryWriteGate;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Shared bounded persistence for typed diagnostic observations, never arbitrary log blobs. */
@Repository
public class DiagnosticRecords {
    private final SqliteConnectionManager database;
    private final HistoryWriteGate gate;
    private final ObjectMapper json = new ObjectMapper();

    public DiagnosticRecords(SqliteConnectionManager database, HistoryWriteGate gate) {
        this.database = database;
        this.gate = gate;
    }

    public void save(String kind, String id, long now, Object value) {
        if (!gate.allowsWrite()) {
            throw new IllegalStateException("历史存储不可用或已达到容量上限");
        }
        final String payload;
        try { payload = json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("无法编码诊断记录", error); }
        if (payload.length() > 262_144) { throw new IllegalArgumentException("诊断记录超过大小限制"); }
        database.write(connection -> {
            try (var statement = connection.prepareStatement("""
                INSERT INTO diagnostic_record(kind,id,updated_at_ms,payload) VALUES(?,?,?,?)
                ON CONFLICT(kind,id) DO UPDATE SET updated_at_ms=excluded.updated_at_ms,payload=excluded.payload
                """)) {
                statement.setString(1, kind); statement.setString(2, id);
                statement.setLong(3, now); statement.setString(4, payload); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                DELETE FROM diagnostic_record WHERE kind=? AND id IN
                (SELECT id FROM diagnostic_record WHERE kind=? ORDER BY updated_at_ms DESC,id DESC LIMIT -1 OFFSET 2000)
                """)) {
                statement.setString(1, kind); statement.setString(2, kind); statement.executeUpdate();
            }
            return null;
        });
    }

    public <T> List<T> list(String kind, Class<T> type, int limit) {
        return database.read(connection -> {
            var result = new ArrayList<T>();
            try (var statement = connection.prepareStatement("""
                SELECT payload FROM diagnostic_record WHERE kind=? ORDER BY updated_at_ms DESC,id DESC LIMIT ?
                """)) {
                statement.setString(1, kind); statement.setInt(2, Math.clamp(limit, 1, 2000));
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        try { result.add(json.readValue(rows.getString(1), type)); }
                        catch (Exception error) { throw new IllegalStateException("诊断记录损坏", error); }
                    }
                }
            }
            return List.copyOf(result);
        });
    }
}
