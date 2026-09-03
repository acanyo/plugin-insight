package com.xhhao.insight.api.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportWorkspaceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesOnlyExpiredInsightExportFilesWhenAcquiringLease() throws Exception {
        Path database = temporaryDirectory.resolve("insight.sqlite");
        Path exports = temporaryDirectory.resolve("exports");
        Files.createDirectories(exports);
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        Path stale = Files.writeString(
            exports.resolve("halo-insight-export-stale.json"), "stale");
        Files.setLastModifiedTime(stale, FileTime.from(now.minus(Duration.ofHours(25))));
        Path fresh = Files.writeString(
            exports.resolve("halo-insight-export-fresh.json"), "fresh");
        Files.setLastModifiedTime(fresh, FileTime.from(now.minus(Duration.ofHours(23))));
        Path unrelated = Files.writeString(exports.resolve("keep.txt"), "keep");
        var workspace = new ExportWorkspace(database,
            Clock.fixed(now, ZoneOffset.UTC), 1_024);

        try (var lease = workspace.acquire()) {
            assertThat(Files.exists(stale)).isFalse();
            assertThat(fresh).exists();
            assertThat(unrelated).exists();
            assertThat(lease.path()).exists();
        }
    }
}
