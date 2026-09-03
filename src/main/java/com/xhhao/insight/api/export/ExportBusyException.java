package com.xhhao.insight.api.export;

import com.xhhao.insight.storage.StorageException;

/** Raised when another export still owns the single export workspace lease. */
public class ExportBusyException extends StorageException {

    public ExportBusyException() {
        super("Another Insight data export is already in progress");
    }
}
