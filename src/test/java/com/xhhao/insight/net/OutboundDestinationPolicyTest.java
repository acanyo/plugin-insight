package com.xhhao.insight.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class OutboundDestinationPolicyTest {

    @Test
    void shouldAllowOnlyHttpsPublicDestinationsByDefault() throws Exception {
        var policy = policyFor("8.8.8.8");

        assertThat(policy.requireHttpsPublic(URI.create("https://cdn.example.com/object")))
            .isEqualTo(URI.create("https://cdn.example.com/object"));
        assertThatThrownBy(() -> policy.requireHttpsPublic(
            URI.create("http://cdn.example.com/object")))
            .isInstanceOf(OutboundDestinationPolicy.DestinationRejectedException.class)
            .hasMessageContaining("HTTPS");
    }

    @Test
    void shouldRejectPrivateLoopbackMetadataAndMixedDnsAnswers() throws Exception {
        assertRejected("127.0.0.1");
        assertRejected("10.1.2.3");
        assertRejected("169.254.169.254");
        assertRejected("192.168.1.5");
        assertRejected("::1");

        var mixed = new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("10.0.0.1")
        });
        assertThatThrownBy(() -> mixed.requireHttpsPublic(
            URI.create("https://cdn.example.com/object")))
            .isInstanceOf(OutboundDestinationPolicy.DestinationRejectedException.class)
            .hasMessageContaining("private or reserved");
    }

    @Test
    void shouldAllowPrivateOnlyThroughExplicitOverride() throws Exception {
        var policy = policyFor("10.1.2.3");

        assertThat(policy.validate(
            URI.create("https://webhook.internal/insight"), true, "Webhook URL"))
            .isEqualTo(URI.create("https://webhook.internal/insight"));
    }

    @Test
    void shouldRejectCredentialsFragmentsAndUnusableAddresses() throws Exception {
        var publicPolicy = policyFor("8.8.8.8");
        assertThatThrownBy(() -> publicPolicy.requireHttpsPublic(
            URI.create("https://user:password@cdn.example.com/object")))
            .hasMessageContaining("credentials");
        assertThatThrownBy(() -> publicPolicy.requireHttpsPublic(
            URI.create("https://cdn.example.com/object#private")))
            .hasMessageContaining("fragment");

        var anyLocalPolicy = policyFor("0.0.0.0");
        assertThatThrownBy(() -> anyLocalPolicy.validate(
            URI.create("https://invalid.example.com"), true, "Webhook URL"))
            .hasMessageContaining("unusable");
    }

    private static OutboundDestinationPolicy policyFor(String address) throws Exception {
        return new OutboundDestinationPolicy(ignored -> new InetAddress[] {
            InetAddress.getByName(address)
        });
    }

    private static void assertRejected(String address) throws Exception {
        var policy = policyFor(address);
        assertThatThrownBy(() -> policy.requireHttpsPublic(
            URI.create("https://cdn.example.com/object")))
            .isInstanceOf(OutboundDestinationPolicy.DestinationRejectedException.class)
            .hasMessageContaining("private or reserved");
    }
}
