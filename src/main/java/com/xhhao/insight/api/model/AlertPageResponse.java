package com.xhhao.insight.api.model;

import com.xhhao.insight.storage.model.AlertIncident;
import java.util.List;

public record AlertPageResponse(List<AlertIncident> items, String nextCursor) {

    public AlertPageResponse {
        items = List.copyOf(items);
    }
}
