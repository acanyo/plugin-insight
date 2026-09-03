package com.xhhao.insight.api.model;

import com.xhhao.insight.storage.model.MonitorEvent;
import java.util.List;

public record EventPageResponse(List<MonitorEvent> items, String nextCursor) {

    public EventPageResponse {
        items = List.copyOf(items);
    }
}
