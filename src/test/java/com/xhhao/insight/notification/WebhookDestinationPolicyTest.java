package com.xhhao.insight.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xhhao.insight.net.OutboundDestinationPolicy;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class WebhookDestinationPolicyTest {

    @Test
    void shouldAllowOnlyHttpsPublicDestinationsByDefault() throws Exception {
        var policy = new WebhookDestinationPolicy(new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName("8.8.8.8")
        }));

        assertThat(policy.validate("https://hooks.example.com/insight", false))
            .isEqualTo(URI.create("https://hooks.example.com/insight"));
        assertThatThrownBy(() -> policy.validate("http://hooks.example.com/insight", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTPS");
    }

    @Test
    void shouldRejectPrivateLoopbackLinkLocalAndMixedDnsAnswers() throws Exception {
        assertRejected("127.0.0.1");
        assertRejected("10.1.2.3");
        assertRejected("169.254.169.254");
        assertRejected("192.168.1.5");
        assertRejected("::1");

        var mixed = new WebhookDestinationPolicy(new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("10.0.0.1")
        }));
        assertThatThrownBy(() -> mixed.validate("https://hooks.example.com", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private or reserved");
    }

    @Test
    void shouldAllowPrivateDestinationOnlyWithExplicitOverride() throws Exception {
        var policy = new WebhookDestinationPolicy(new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName("10.1.2.3")
        }));

        assertThat(policy.validate("https://webhook.internal/insight", true))
            .isEqualTo(URI.create("https://webhook.internal/insight"));
    }

    @Test
    void shouldStillRejectCredentialsAndUnusableAddressesWithOverride() throws Exception {
        var publicPolicy = new WebhookDestinationPolicy(new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName("8.8.8.8")
        }));
        assertThatThrownBy(() -> publicPolicy.validate(
            "https://user:password@hooks.example.com", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credentials");

        var anyLocalPolicy = new WebhookDestinationPolicy(new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName("0.0.0.0")
        }));
        assertThatThrownBy(() -> anyLocalPolicy.validate("https://invalid.example.com", true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unusable");
    }

    private static void assertRejected(String address) throws Exception {
        var policy = new WebhookDestinationPolicy(new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName(address)
        }));
        assertThatThrownBy(() -> policy.validate("https://hooks.example.com", false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private or reserved");
    }
}
