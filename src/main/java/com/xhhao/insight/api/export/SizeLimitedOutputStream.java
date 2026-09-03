package com.xhhao.insight.api.export;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class SizeLimitedOutputStream extends FilterOutputStream {

    private final long maximumBytes;
    private long writtenBytes;

    SizeLimitedOutputStream(OutputStream output, long maximumBytes) {
        super(output);
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    public void write(int value) throws IOException {
        ensureCapacity(1);
        out.write(value);
        writtenBytes++;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes must not be null");
        }
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(length);
        out.write(bytes, offset, length);
        writtenBytes += length;
    }

    private void ensureCapacity(int nextBytes) {
        if (nextBytes > maximumBytes - writtenBytes) {
            throw new ExportCapacityException(
                "Insight export exceeded the maximum size of " + maximumBytes + " bytes");
        }
    }
}
