package com.xhhao.insight.diagnostics.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class BoundedLogFileReaderTest {
    @TempDir Path temporary;

    @Test
    void readsOnlyCompleteNewLinesAndRetainsIncompleteUtf8UntilCompletion() throws Exception {
        Path path = temporary.resolve("halo.log");
        Files.writeString(path, "INFO [worker] example.Worker - first\n");
        var reader = new BoundedLogFileReader();
        var initial = reader.read(path, null);
        Files.writeString(
                path, "ERROR [worker] example.Worker - 异常 token=secret", StandardOpenOption.APPEND);
        var partial = reader.read(path, initial.cursor());
        assertThat(partial.text()).isEmpty();
        assertThat(partial.cursor()).isEqualTo(initial.cursor());
        Files.writeString(path, "\n", StandardOpenOption.APPEND);
        var complete = reader.read(path, partial.cursor());
        assertThat(complete.text()).contains("异常 token=secret").doesNotContain("first");
        assertThat(complete.reset()).isFalse();
    }

    @Test
    void detectsRotationAndSameSizeRewriteWithoutTreatingThemAsAppend() throws Exception {
        Path path = temporary.resolve("halo.log");
        Files.writeString(path, "INFO old\n");
        var reader = new BoundedLogFileReader();
        var initial = reader.read(path, null);
        Files.writeString(path, "INFO new\n");
        var rewritten = reader.read(path, initial.cursor());
        assertThat(rewritten.reset()).isTrue();
        assertThat(rewritten.text()).contains("new");
        Files.move(path, temporary.resolve("halo.log.1"));
        Files.writeString(path, "INFO rotated\n");
        assertThat(reader.read(path, rewritten.cursor()).reset()).isTrue();
    }

    @Test
    void boundsNewDataAndDiscardsTheContinuationOfOversizedLines() throws Exception {
        Path path = temporary.resolve("halo.log");
        Files.writeString(path, "INFO first\n");
        var reader = new BoundedLogFileReader();
        var initial = reader.read(path, null);
        Files.writeString(
                path, "x".repeat(BoundedLogFileReader.MAX_BYTES + 1), StandardOpenOption.APPEND);
        var gap = reader.read(path, initial.cursor());
        assertThat(gap.gap()).isTrue();
        assertThat(gap.cursor().skippingLine()).isTrue();
        Files.writeString(path, "continuation\nINFO final\n", StandardOpenOption.APPEND);
        var complete = reader.read(path, gap.cursor());
        assertThat(complete.text()).isEqualTo("INFO final\n");
        assertThat(complete.cursor().skippingLine()).isFalse();
    }
}
