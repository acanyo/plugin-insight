package com.xhhao.insight.storage.database;

import com.xhhao.insight.storage.StorageException;

public class SchemaVersionException extends StorageException {

    private final int databaseVersion;
    private final int supportedVersion;

    public SchemaVersionException(int databaseVersion, int supportedVersion) {
        super("Database schema version " + databaseVersion
            + " is newer than supported version " + supportedVersion);
        this.databaseVersion = databaseVersion;
        this.supportedVersion = supportedVersion;
    }

    public int databaseVersion() {
        return databaseVersion;
    }

    public int supportedVersion() {
        return supportedVersion;
    }
}
