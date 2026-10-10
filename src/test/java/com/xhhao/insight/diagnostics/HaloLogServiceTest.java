package com.xhhao.insight.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.insight.diagnostics.logging.PluginLogClassIndex;
import com.xhhao.insight.halo.HaloHostContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class HaloLogServiceTest {
    @TempDir Path temporary;
    private HaloLogService service;
    private Path file;

    @BeforeEach
    void configureFileLog() throws Exception {
        file = temporary.resolve("halo.log");
        Files.writeString(
                file,
                """
2026-10-08T19:00:00.000+08:00 ERROR 1 --- [worker] c.e.alpha.Worker : alpha failed
java.lang.IllegalStateException: unavailable
\tat com.example.alpha.Worker.run(Worker.java:23)
2026-10-08T19:00:01.000+08:00 INFO 1 --- [worker] com.example.beta.Worker : alpha was stopped
2026-10-08T19:00:02.000+08:00 ERROR 1 --- [worker] com.example.beta.Worker : beta failed
java.lang.IllegalArgumentException: invalid
\tat com.example.beta.Worker.run(Worker.java:24)
2026-10-08T19:00:03.000+08:00 WARN 1 --- [worker] org.halo.Host : alpha is mentioned here
2026-10-08T19:00:04.000+08:00 WARN 1 --- [worker] com.example.alpha.Worker : retry token=private-token
""");
        var root = mock(ApplicationContext.class);
        when(root.getEnvironment())
                .thenReturn(
                        new MockEnvironment().withProperty("logging.file.name", file.toString()));
        var host = mock(HaloHostContext.class);
        when(host.root()).thenReturn(root);
        var index = mock(PluginLogClassIndex.class);
        when(index.snapshot())
                .thenReturn(
                        new PluginLogClassIndex.Snapshot(
                                List.of(
                                        new PluginLogClassIndex.PluginOption("alpha", "甲插件", true),
                                        new PluginLogClassIndex.PluginOption("beta", "乙插件", true)),
                                Map.of(
                                        "com.example.alpha.Worker",
                                        Set.of("alpha"),
                                        "com.example.beta.Worker",
                                        Set.of("beta")),
                                Map.of(
                                        "Worker",
                                        List.of(
                                                "com.example.alpha.Worker",
                                                "com.example.beta.Worker")),
                                true));
        service = new HaloLogService(host, index);
    }

    @Test
    void filtersByPluginBeforePaginatingAndAggregatingExceptions() throws Exception {
        var result = service.read(0, "all", "", 1, 1, "alpha");
        assertThat(result.pluginId()).isEqualTo("alpha");
        assertThat(result.totalEntries()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().getFirst().text())
                .contains("retry", "[REDACTED]")
                .doesNotContain("private-token");
        assertThat(result.exceptions()).hasSize(1);
        assertThat(result.exceptions().getFirst().signature())
                .contains("IllegalStateException")
                .doesNotContain("IllegalArgumentException");
        var secondPage = service.read(0, "all", "", 2, 1, "alpha");
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.entries().getFirst().text()).contains("alpha failed");
        assertThat(service.read(0, "WARN", "RETRY", 1, 20, "alpha").exceptions()).isEmpty();
        assertThat(service.read(0, "ERROR", "invalid", 1, 20, "alpha").totalEntries()).isZero();
    }

    @Test
    void allLogsRetainUnattributedHostMessagesAndDoNotUsePluginMentionsAsOwnership()
            throws Exception {
        var all = service.read(0, "all", "", 1, 20, "");
        assertThat(all.totalEntries()).isEqualTo(5);
        assertThat(all.unattributedEntries()).isEqualTo(1);
        assertThat(all.entries())
                .anySatisfy(
                        entry -> {
                            assertThat(entry.text()).contains("alpha is mentioned here");
                            assertThat(entry.pluginIds()).isEmpty();
                            assertThat(entry.attribution()).isEqualTo("unknown");
                        });
        assertThat(service.read(0, "INFO", "alpha", 1, 20, "alpha").entries()).isEmpty();
        assertThat(service.read(0, "INFO", "alpha", 1, 20, "beta").entries()).hasSize(1);
    }

    @Test
    void rejectsUnknownPluginsAndInvalidPagination() {
        assertThatThrownBy(() -> service.read(0, "all", "", 1, 20, "removed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("插件不存在");
        assertThatThrownBy(() -> service.read(0, "all", "", 0, 20, "alpha"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.read(0, "all", "", 1, 101, "alpha"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsFileTailAndDoesNotIncludeAnOldPluginEntry() throws Exception {
        Files.writeString(
                file,
                "INFO [worker] com.example.alpha.Worker - old\n"
                        + "x".repeat(512 * 1024)
                        + "\nINFO [worker] com.example.beta.Worker - newest\n");
        var result = service.read(0, "all", "", 1, 20, "alpha");
        assertThat(result.truncated()).isTrue();
        assertThat(result.entries()).isEmpty();
        assertThat(service.read(0, "all", "", 1, 20, "beta").entries()).hasSize(1);
    }

    @Test
    void keepsPaginationStableWhileIncrementalReadsCreateANewSnapshot() throws Exception {
        var initial = service.read(0, "all", "", 1, 1, "alpha");
        Files.writeString(
                file,
                "INFO [worker] com.example.alpha.Worker - added\n",
                java.nio.file.StandardOpenOption.APPEND);
        var fixedPage = service.read(0, "all", "", 2, 1, "alpha", initial.snapshotId(), "page");
        assertThat(fixedPage.totalEntries()).isEqualTo(2);
        assertThat(fixedPage.entries().getFirst().text()).contains("alpha failed");
        var updated =
                service.read(0, "all", "", 1, 20, "alpha", initial.snapshotId(), "incremental");
        assertThat(updated.totalEntries()).isEqualTo(3);
        assertThat(updated.snapshotId()).isNotEqualTo(initial.snapshotId());
        assertThat(updated.entries().getFirst().text()).contains("added");
        assertThat(
                        service.read(0, "all", "", 1, 20, "alpha", initial.snapshotId(), "page")
                                .totalEntries())
                .isEqualTo(2);
    }

    @Test
    void attachesAppendedStackFramesToTheLastEntryAndKeepsSecretsRedacted() throws Exception {
        var initial = service.read(0, "all", "", 1, 20, "beta");
        Files.writeString(
                file,
                "java.lang.IllegalStateException: token=new-secret\n"
                        + "\tat com.example.beta.Worker.run(Worker.java:99)\n",
                java.nio.file.StandardOpenOption.APPEND);
        var updated =
                service.read(0, "all", "", 1, 20, "beta", initial.snapshotId(), "incremental");
        assertThat(updated.totalEntries()).isEqualTo(3);
        assertThat(updated.entries().getFirst().pluginIds())
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(updated.entries().getFirst().text())
                .contains("Worker.java:99", "[REDACTED]")
                .doesNotContain("new-secret");
        assertThat(
                        service.read(0, "all", "", 1, 20, "beta", initial.snapshotId(), "page")
                                .totalEntries())
                .isEqualTo(2);
    }
}
