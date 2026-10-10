package com.xhhao.insight.diagnostics.logging;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Reads complete lines only and verifies the previous offset before incremental reads. */
@Component
public class BoundedLogFileReader {
    public static final int MAX_BYTES = 512 * 1024;

    public Window read(Path path, Cursor previous) throws Exception {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class);
        String key = String.valueOf(attributes.fileKey());
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            boolean reset =
                    previous != null
                            && (!previous.path().equals(path)
                                    || !previous.fileKey().equals(key)
                                    || size < previous.offset()
                                    || !anchor(channel, previous.offset())
                                            .equals(previous.anchor()));
            boolean gap = previous != null && !reset && size - previous.offset() > MAX_BYTES;
            long start =
                    previous == null || reset || gap
                            ? Math.max(0, size - MAX_BYTES)
                            : previous.offset();
            boolean truncated = start > 0 && (previous == null || reset || gap);
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(MAX_BYTES, size - start));
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {}
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            int begin = 0;
            boolean discardLine =
                    truncated && !atLineBoundary(channel, start)
                            || previous != null && !reset && !gap && previous.skippingLine();
            if (discardLine) {
                while (begin < bytes.length && bytes[begin] != '\n') begin++;
                if (begin < bytes.length) begin++;
            }
            int end = bytes.length;
            while (end > begin && bytes[end - 1] != '\n') end--;
            long offset = start + end;
            boolean skippingLine =
                    discardLine
                            && begin == bytes.length
                            && (bytes.length == 0 || bytes[bytes.length - 1] != '\n');
            var cursor = new Cursor(path, key, offset, anchor(channel, offset), skippingLine);
            return new Window(
                    new String(bytes, begin, end - begin, StandardCharsets.UTF_8),
                    cursor,
                    truncated,
                    reset,
                    gap);
        }
    }

    private static boolean atLineBoundary(FileChannel channel, long start) throws Exception {
        if (start == 0) return true;
        var last = ByteBuffer.allocate(1);
        channel.position(start - 1);
        channel.read(last);
        return last.position() > 0 && last.get(0) == '\n';
    }

    private static String anchor(FileChannel channel, long offset) throws Exception {
        int length = (int) Math.min(128, offset);
        ByteBuffer bytes = ByteBuffer.allocate(length);
        channel.position(offset - length);
        while (bytes.hasRemaining() && channel.read(bytes) > 0) {}
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    }

    public record Cursor(
            Path path, String fileKey, long offset, String anchor, boolean skippingLine) {}

    public record Window(
            String text, Cursor cursor, boolean truncated, boolean reset, boolean gap) {}
}
