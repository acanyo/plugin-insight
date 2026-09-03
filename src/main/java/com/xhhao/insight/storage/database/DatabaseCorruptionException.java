package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;

public class DatabaseCorruptionException extends StorageException {

    public DatabaseCorruptionException(String message) {
        super(message);
    }
}
