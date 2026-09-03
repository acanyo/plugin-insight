package com.xhhao.insight.notification;

import com.xhhao.insight.net.OutboundDestinationPolicy;
import java.net.URI;
import org.springframework.stereotype.Component;

/** Validates webhook destinations before the server opens an outbound connection. */
@Component
public class WebhookDestinationPolicy {

    private final OutboundDestinationPolicy outboundPolicy;

    public WebhookDestinationPolicy(OutboundDestinationPolicy outboundPolicy) {
        this.outboundPolicy = outboundPolicy;
    }

    public URI validate(String rawUrl, boolean allowPrivateNetwork) {
        URI uri;
        try {
            uri = URI.create(rawUrl == null ? "" : rawUrl.strip());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Webhook URL is invalid");
        }
        return outboundPolicy.validate(uri, allowPrivateNetwork, "Webhook URL");
    }
}
