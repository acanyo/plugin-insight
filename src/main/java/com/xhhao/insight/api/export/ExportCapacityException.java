package com.xhhao.insight.api.export;

import com.xhhao.insight.storage.StorageException;

/** Raised before an export can consume unsafe amounts of plugin storage. */
public class ExportCapacityException extends StorageException {

    public ExportCapacityException(String message) {
        super(message);
    }

    public ExportCapacityException(String message, Throwable cause) {
        super(message, cause);
    }
}
