package com.xhhao.insight.storage.model;

import java.util.List;

public record MonitorEventPage(
    List<MonitorEvent> items,
    MonitorEventCursor nextCursor
) {

    public MonitorEventPage {
        items = List.copyOf(items);
    }

    public boolean hasMore() {
        return nextCursor != null;
    }
}
