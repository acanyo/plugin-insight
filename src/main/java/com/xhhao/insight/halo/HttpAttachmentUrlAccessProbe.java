package com.xhhao.insight.halo;

import com.xhhao.insight.halo.AttachmentUrlAccessProbe.Result;
import com.xhhao.insight.net.OutboundDestinationPolicy;
import com.xhhao.insight.net.OutboundDestinationPolicy.DestinationRejectedException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.infra.utils.HttpSecurityUtils;

/** Uses a body-free HTTP HEAD request and never follows redirects. */
@Component
public final class HttpAttachmentUrlAccessProbe implements AttachmentUrlAccessProbe {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);

    private final WebClient webClient;
    private final OutboundDestinationPolicy destinationPolicy;

    public HttpAttachmentUrlAccessProbe(OutboundDestinationPolicy destinationPolicy) {
        this.destinationPolicy = destinationPolicy;
        this.webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpSecurityUtils.secureHttpClient()
                .responseTimeout(REQUEST_TIMEOUT)))
            .build();
    }

    @Override
    public Mono<Result> probe(URI uri) {
        if (uri == null || !uri.isAbsolute()) {
            return Mono.just(Result.inconclusive(
                "已解析相对访问地址，但 Halo 未配置可供后台探测的外部地址"));
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme)) {
            return Mono.just(Result.inconclusive(
                "仅允许探测使用 HTTPS 的公网附件地址"));
        }

        return Mono.defer(() -> {
                URI safeDestination = destinationPolicy.requireHttpsPublic(uri);
                return webClient.method(HttpMethod.HEAD)
                    .uri(safeDestination)
                    .header("User-Agent", "Halo-Insight/1.0")
                    .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(classify(response.statusCode().value())));
            })
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(REQUEST_TIMEOUT.plusMillis(500))
            .onErrorResume(DestinationRejectedException.class, error -> Mono.just(
                Result.inconclusive("附件地址不是可验证的 HTTPS 公网端点，已跳过网络请求")))
            .onErrorResume(error -> Mono.just(failed(error)));
    }

    static Result classify(int statusCode) {
        if (statusCode >= 200 && statusCode < 400) {
            return Result.reachable("附件访问端点可达（HTTP " + statusCode + "）");
        }
        if (statusCode == 401 || statusCode == 403 || statusCode == 405) {
            return Result.inconclusive(
                "端点可达，但不允许无请求体的 HEAD 探测（HTTP " + statusCode + "）");
        }
        return Result.unreachable("附件访问端点返回 HTTP " + statusCode);
    }

    static Result failed(Throwable error) {
        Throwable cause = rootCause(error);
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return Result.unreachable("附件访问端点探测超时");
        }
        if (cause instanceof UnknownHostException) {
            return Result.unreachable("无法解析附件存储域名");
        }
        if (cause instanceof ConnectException) {
            return Result.unreachable("无法连接附件存储端点");
        }
        if (cause instanceof SSLException) {
            return Result.unreachable("附件存储端点 TLS 握手失败");
        }
        return Result.unreachable(
            "附件访问端点探测失败（" + cause.getClass().getSimpleName() + "）");
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
