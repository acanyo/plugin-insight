package com.xhhao.insight.halo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.xhhao.insight.net.OutboundDestinationPolicy;
import com.xhhao.insight.net.OutboundDestinationPolicy.DestinationRejectedException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class HttpAttachmentUrlAccessProbeTest {

    @Test
    void shouldClassifySuccessfulAndRedirectResponsesAsReachable() {
        assertThat(HttpAttachmentUrlAccessProbe.classify(200).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.REACHABLE);
        assertThat(HttpAttachmentUrlAccessProbe.classify(302).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.REACHABLE);
    }

    @Test
    void shouldClassifyHeadRestrictionsAsInconclusive() {
        assertThat(HttpAttachmentUrlAccessProbe.classify(401).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.INCONCLUSIVE);
        assertThat(HttpAttachmentUrlAccessProbe.classify(403).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.INCONCLUSIVE);
        assertThat(HttpAttachmentUrlAccessProbe.classify(405).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.INCONCLUSIVE);
    }

    @Test
    void shouldClassifyStorageErrorsAsUnreachable() {
        assertThat(HttpAttachmentUrlAccessProbe.classify(404).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.UNREACHABLE);
        assertThat(HttpAttachmentUrlAccessProbe.classify(503).outcome())
            .isEqualTo(AttachmentUrlAccessProbe.Outcome.UNREACHABLE);
    }

    @Test
    void shouldNotSendNetworkRequestForRelativeOrNonHttpUrls() {
        OutboundDestinationPolicy destinationPolicy = mock(OutboundDestinationPolicy.class);
        HttpAttachmentUrlAccessProbe probe = new HttpAttachmentUrlAccessProbe(destinationPolicy);

        StepVerifier.create(probe.probe(URI.create("/upload/example.png")))
            .assertNext(result -> {
                assertThat(result.outcome())
                    .isEqualTo(AttachmentUrlAccessProbe.Outcome.INCONCLUSIVE);
                assertThat(result.detail()).contains("相对访问地址");
            })
            .verifyComplete();

        StepVerifier.create(probe.probe(URI.create("http://169.254.169.254/latest/meta-data")))
            .assertNext(result -> {
                assertThat(result.outcome())
                    .isEqualTo(AttachmentUrlAccessProbe.Outcome.INCONCLUSIVE);
                assertThat(result.detail()).contains("HTTPS");
            })
            .verifyComplete();

        verifyNoInteractions(destinationPolicy);
    }

    @Test
    void shouldReportPrivateOrReservedHttpsDestinationAsInconclusive() {
        URI metadataUri = URI.create("https://169.254.169.254/latest/meta-data?token=private");
        OutboundDestinationPolicy destinationPolicy = mock(OutboundDestinationPolicy.class);
        when(destinationPolicy.requireHttpsPublic(metadataUri))
            .thenThrow(new DestinationRejectedException(
                "Destination host resolves to a private or reserved network address"));
        HttpAttachmentUrlAccessProbe probe = new HttpAttachmentUrlAccessProbe(destinationPolicy);

        StepVerifier.create(probe.probe(metadataUri))
            .assertNext(result -> {
                assertThat(result.outcome())
                    .isEqualTo(AttachmentUrlAccessProbe.Outcome.INCONCLUSIVE);
                assertThat(result.detail())
                    .contains("已跳过网络请求")
                    .doesNotContain("token=private");
            })
            .verifyComplete();
    }

    @Test
    void shouldSanitizeConnectionFailureMessages() {
        assertThat(HttpAttachmentUrlAccessProbe.failed(
            new HttpTimeoutException("contains-sensitive-url"))).extracting(
                AttachmentUrlAccessProbe.Result::detail)
            .isEqualTo("附件访问端点探测超时");
        assertThat(HttpAttachmentUrlAccessProbe.failed(
            new UnknownHostException("secret.example"))).extracting(
                AttachmentUrlAccessProbe.Result::detail)
            .isEqualTo("无法解析附件存储域名");
        assertThat(HttpAttachmentUrlAccessProbe.failed(
            new ConnectException("https://secret.example/token"))).extracting(
                AttachmentUrlAccessProbe.Result::detail)
            .isEqualTo("无法连接附件存储端点");
        assertThat(HttpAttachmentUrlAccessProbe.failed(
            new SSLHandshakeException("private certificate detail"))).extracting(
                AttachmentUrlAccessProbe.Result::detail)
            .isEqualTo("附件存储端点 TLS 握手失败");
    }
}
