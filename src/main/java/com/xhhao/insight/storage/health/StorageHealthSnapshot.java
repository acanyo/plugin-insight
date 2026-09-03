package com.xhhao.insight.storage.health;

public record StorageHealthSnapshot(
    StorageState state,
    String message,
    long checkedAtMs,
    long failureCount
) {

    public boolean available() {
        return state == StorageState.AVAILABLE;
    }
}
