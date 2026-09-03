package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;

/**
 * Indicates that a database claims a supported version but its required objects are incompatible.
 */
public class SchemaValidationException extends StorageException {

    public SchemaValidationException(String message) {
        super(message);
    }
}
