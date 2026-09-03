package com.xhhao.insight.storage.model;

import java.util.List;

public record AlertIncidentPage(List<AlertIncident> items, AlertIncidentCursor nextCursor) {

    public AlertIncidentPage {
        items = List.copyOf(items);
    }
}
