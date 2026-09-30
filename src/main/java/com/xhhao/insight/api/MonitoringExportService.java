package com.xhhao.insight.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.insight.api.export.ExportWorkspace;
import com.xhhao.insight.storage.StorageException;
import com.xhhao.insight.storage.database.SqliteConnectionManager;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Writes a complete range export directly from SQLite to a temporary JSON file.
 * Rows are streamed through Jackson so export size does not become heap usage.
 */
@Component
public class MonitoringExportService {

    private static final DateTimeFormatter FILE_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final List<ExportTable> TABLES = List.of(
        new ExportTable("systemSamples", "system_sample_15s", "sampled_at_ms"),
        new ExportTable("systemMetricMinutes", "system_metric_minute", "bucket_start_ms"),
        new ExportTable("requestMetrics", "request_metric_minute", "bucket_start_ms"),
        new ExportTable("pluginMetrics", "plugin_metric_minute", "bucket_start_ms"),
        new ExportTable("pluginResourceMetrics", "plugin_resource_minute", "bucket_start_ms"),
        new ExportTable(
            "resourceAttributionMetrics", "resource_attribution_minute", "bucket_start_ms"),
        new ExportTable("events", "monitor_event", "occurred_at_ms"),
        new ExportTable("alerts", "alert_incident", "updated_at_ms"),
        new ExportTable("diagnostics", "diagnostic_record", "updated_at_ms", "*",
            "kind IN ('memory','tasks','optimization-observation')"),
        new ExportTable("notificationDeliveries", "notification_delivery", "updated_at_ms",
            "id,channel,state,attempts,next_at_ms,created_at_ms,updated_at_ms,message", "1=1")
    );

    private final SqliteConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final ExportWorkspace workspace;
    private final Clock clock;

    @Autowired
    public MonitoringExportService(
        SqliteConnectionManager connectionManager,
        ExportWorkspace workspace
    ) {
        this(connectionManager, new ObjectMapper(), workspace, Clock.systemUTC());
    }

    MonitoringExportService(
        SqliteConnectionManager connectionManager,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this(connectionManager, objectMapper,
            new ExportWorkspace(connectionManager.databasePath(), clock,
                ExportWorkspace.DEFAULT_MAXIMUM_BYTES), clock);
    }

    MonitoringExportService(
        SqliteConnectionManager connectionManager,
        ObjectMapper objectMapper,
        ExportWorkspace workspace,
        Clock clock
    ) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.workspace = workspace;
        this.clock = clock;
    }

    public void validateRange(String rangeName) {
        ApiTimeRange.parse(rangeName, clock.millis());
    }

    public ExportFile create(String rangeName) {
        long generatedAtMs = clock.millis();
        ApiTimeRange range = ApiTimeRange.parse(rangeName, generatedAtMs);
        ExportWorkspace.Lease lease = workspace.acquire();
        try {
            write(lease, range, generatedAtMs);
            String fileName = "halo-insight-" + range.name() + '-'
                + FILE_DATE.format(Instant.ofEpochMilli(generatedAtMs)) + ".json";
            return new ExportFile(lease, Files.size(lease.path()), fileName);
        } catch (IOException | RuntimeException error) {
            lease.close();
            if (error instanceof StorageException storageError) {
                throw storageError;
            }
            throw new StorageException("Failed to create Insight monitoring export", error);
        }
    }

    private void write(ExportWorkspace.Lease lease, ApiTimeRange range, long generatedAtMs)
        throws IOException {
        try (OutputStream output = lease.openOutput();
             JsonGenerator json = objectMapper.getFactory().createGenerator(output)) {
            json.writeStartObject();
            json.writeNumberField("schemaVersion", 5);
            json.writeNumberField("generatedAtMs", generatedAtMs);
            json.writeStringField("range", range.name());
            json.writeNumberField("fromMs", range.fromMs());
            json.writeNumberField("toMs", range.toMs());
            json.writeBooleanField("storageAvailable", true);
            json.writeStringField("consistency", "table-snapshot");
            connectionManager.readTransaction(connection -> {
                try {
                    for (ExportTable table : TABLES) {
                        writeTable(json, connection, table, range);
                    }
                    return null;
                } catch (IOException error) {
                    throw new SQLException("Failed while encoding Insight export", error);
                }
            });
            json.writeEndObject();
        }
    }

    private static void writeTable(
        JsonGenerator json,
        Connection connection,
        ExportTable table,
        ApiTimeRange range
    ) throws SQLException, IOException {
        json.writeArrayFieldStart(table.jsonName());
        String sql = "SELECT " + table.columns() + " FROM " + table.tableName()
            + " WHERE " + table.timeColumn() + " >= " + range.fromMs()
            + " AND " + table.timeColumn() + " < " + range.toMs()
            + " AND " + table.condition()
            + " ORDER BY " + table.timeColumn() + " ASC";
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                json.writeStartObject();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    writeValue(json, camelCase(metadata.getColumnLabel(index)), rows.getObject(index));
                }
                json.writeEndObject();
            }
        }
        json.writeEndArray();
    }

    private static void writeValue(JsonGenerator json, String name, Object value)
        throws IOException {
        if (value == null) {
            json.writeNullField(name);
        } else if (value instanceof Integer number) {
            json.writeNumberField(name, number);
        } else if (value instanceof Long number) {
            json.writeNumberField(name, number);
        } else if (value instanceof Float number) {
            json.writeNumberField(name, number);
        } else if (value instanceof Double number) {
            json.writeNumberField(name, number);
        } else if (value instanceof byte[] bytes) {
            json.writeBinaryField(name, bytes);
        } else {
            json.writeStringField(name, value.toString());
        }
    }

    private static String camelCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(character));
                upperNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    public static final class ExportFile implements AutoCloseable {

        private final ExportWorkspace.Lease lease;
        private final long sizeBytes;
        private final String fileName;

        private ExportFile(ExportWorkspace.Lease lease, long sizeBytes, String fileName) {
            this.lease = lease;
            this.sizeBytes = sizeBytes;
            this.fileName = fileName;
        }

        public Path path() {
            return lease.path();
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public String fileName() {
            return fileName;
        }

        @Override
        public void close() {
            lease.close();
        }
    }

    private record ExportTable(String jsonName, String tableName, String timeColumn, String columns, String condition) {
        ExportTable(String jsonName, String tableName, String timeColumn) {
            this(jsonName, tableName, timeColumn, "*", "1=1");
        }
    }
}
