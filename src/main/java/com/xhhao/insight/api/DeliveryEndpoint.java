package com.xhhao.insight.api;

import com.xhhao.insight.notification.DeliveryRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class DeliveryEndpoint implements CustomEndpoint {
    private final DeliveryRepository deliveries;
    private final DiagnosticHttp http;
    public DeliveryEndpoint(DeliveryRepository deliveries, DiagnosticHttp http) { this.deliveries = deliveries; this.http = http; }
    @Override public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route().GET("deliveries", request -> http.json(() -> deliveries.list(false, System.currentTimeMillis(), 100)
            .stream().map(row -> new DeliveryView(row.id(), row.channel(), row.state(), row.attempts(), row.nextAtMs(),
                row.createdAtMs(), row.updatedAtMs(), row.message())).toList())).build();
    }
    @Override public GroupVersion groupVersion() { return GroupVersion.parseAPIVersion("console.api.insight.halo.run/v1alpha1"); }
    public record DeliveryView(String id, String channel, String state, int attempts,
                               long nextAtMs, long createdAtMs, long updatedAtMs, String message) {}
}
