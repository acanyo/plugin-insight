package com.xhhao.insight.halo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.PolicyTemplate;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalLinkProcessor;

@ExtendWith(MockitoExtension.class)
class AttachmentStorageProbeTest {

    @Mock
    ReactiveExtensionClient extensionClient;

    @Mock
    AttachmentService attachmentService;

    @Mock
    ExternalLinkProcessor externalLinkProcessor;

    @Mock
    AttachmentUrlAccessProbe urlAccessProbe;

    AttachmentStorageProbe probe;

    @BeforeEach
    void setUp() {
        probe = new AttachmentStorageProbe(extensionClient, attachmentService,
            externalLinkProcessor, urlAccessProbe);
    }

    @Test
    void shouldReportWarningWhenNoPolicyIsConfigured() {
        stubPolicies(Flux.empty());

        StepVerifier.create(probe.probe())
            .assertNext(status -> {
                assertThat(status.status()).isEqualTo("warning");
                assertThat(status.message()).isEqualTo("未配置存储策略");
            })
            .verifyComplete();
    }

    @Test
    void shouldReportHealthyAfterExistingAttachmentEndpointResponds() {
        Policy policy = policy("default-policy", "本地存储", "local", "local-config");
        Attachment attachment = attachment("attachment-1", "default-policy");
        URI handlerUri = URI.create("/upload/example.png");
        URI externalUri = URI.create("https://example.test/upload/example.png");
        stubValidPolicy(policy);
        stubAttachment(policy, Flux.just(attachment));
        when(attachmentService.getSharedURL(eq(attachment), any(Duration.class)))
            .thenReturn(Mono.just(handlerUri));
        when(externalLinkProcessor.processLink(handlerUri)).thenReturn(Mono.just(externalUri));
        when(urlAccessProbe.probe(externalUri)).thenReturn(Mono.just(
            AttachmentUrlAccessProbe.Result.reachable("附件访问端点可达（HTTP 200）")));

        StepVerifier.create(probe.probe())
            .assertNext(status -> {
                assertThat(status.status()).isEqualTo("healthy");
                assertThat(status.message()).contains("1 个可达");
            })
            .verifyComplete();
    }

    @Test
    void shouldStayUnknownWithoutExistingAttachmentAndNeverUploadProbeFile() {
        Policy policy = policy("empty-policy", "空存储", "s3", "s3-config");
        stubValidPolicy(policy);
        stubAttachment(policy, Flux.empty());

        StepVerifier.create(probe.probe())
            .assertNext(status -> {
                assertThat(status.status()).isEqualTo("unknown");
                assertThat(status.message())
                    .contains("没有现有附件可供只读探测")
                    .contains("不会上传测试文件");
            })
            .verifyComplete();

        verifyNoInteractions(attachmentService);
    }

    @Test
    void shouldReportUnknownWhenEndpointRejectsHeadProbe() {
        Policy policy = policy("s3-policy", "对象存储", "s3", "s3-config");
        Attachment attachment = attachment("attachment-1", "s3-policy");
        URI accessUri = URI.create("https://objects.example.test/signed-object");
        stubValidPolicy(policy);
        stubAttachment(policy, Flux.just(attachment));
        when(attachmentService.getSharedURL(eq(attachment), any(Duration.class)))
            .thenReturn(Mono.just(accessUri));
        when(externalLinkProcessor.processLink(accessUri)).thenReturn(Mono.just(accessUri));
        when(urlAccessProbe.probe(accessUri)).thenReturn(Mono.just(
            AttachmentUrlAccessProbe.Result.inconclusive(
                "端点可达，但不允许无请求体的 HEAD 探测（HTTP 403）")));

        StepVerifier.create(probe.probe())
            .assertNext(status -> {
                assertThat(status.status()).isEqualTo("unknown");
                assertThat(status.message()).contains("1 个受限").contains("HTTP 403");
            })
            .verifyComplete();
    }

    @Test
    void shouldReportWarningWhenPolicyConfigurationIsMissing() {
        Policy policy = policy("broken-policy", "故障存储", "s3", "missing-config");
        stubPolicies(Flux.just(policy));
        when(extensionClient.fetch(PolicyTemplate.class, "s3"))
            .thenReturn(Mono.just(new PolicyTemplate()));
        when(extensionClient.fetch(ConfigMap.class, "missing-config")).thenReturn(Mono.empty());

        StepVerifier.create(probe.probe())
            .assertNext(status -> {
                assertThat(status.status()).isEqualTo("warning");
                assertThat(status.message()).contains("关联的存储配置不存在");
            })
            .verifyComplete();
    }

    @Test
    void shouldFallbackToPermalinkWhenSharedUrlIsUnsupported() {
        Policy policy = policy("default-policy", "本地存储", "local", "local-config");
        Attachment attachment = attachment("attachment-1", "default-policy");
        URI accessUri = URI.create("https://example.test/upload/example.png");
        stubValidPolicy(policy);
        stubAttachment(policy, Flux.just(attachment));
        when(attachmentService.getSharedURL(eq(attachment), any(Duration.class)))
            .thenReturn(Mono.empty());
        when(attachmentService.getPermalink(attachment)).thenReturn(Mono.just(accessUri));
        when(externalLinkProcessor.processLink(accessUri)).thenReturn(Mono.just(accessUri));
        when(urlAccessProbe.probe(accessUri)).thenReturn(Mono.just(
            AttachmentUrlAccessProbe.Result.reachable("附件访问端点可达（HTTP 204）")));

        StepVerifier.create(probe.probe())
            .assertNext(status -> assertThat(status.status()).isEqualTo("healthy"))
            .verifyComplete();
    }

    private void stubValidPolicy(Policy policy) {
        stubPolicies(Flux.just(policy));
        when(extensionClient.fetch(PolicyTemplate.class, policy.getSpec().getTemplateName()))
            .thenReturn(Mono.just(new PolicyTemplate()));
        when(extensionClient.fetch(ConfigMap.class, policy.getSpec().getConfigMapName()))
            .thenReturn(Mono.just(new ConfigMap()));
    }

    private void stubAttachment(Policy policy, Flux<Attachment> attachments) {
        when(extensionClient.listAll(eq(Attachment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(attachments);
    }

    private void stubPolicies(Flux<Policy> policies) {
        when(extensionClient.listAll(eq(Policy.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(policies);
    }

    private static Policy policy(
        String name,
        String displayName,
        String templateName,
        String configMapName
    ) {
        Policy policy = new Policy();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        policy.setMetadata(metadata);
        Policy.PolicySpec spec = new Policy.PolicySpec();
        spec.setDisplayName(displayName);
        spec.setTemplateName(templateName);
        spec.setConfigMapName(configMapName);
        policy.setSpec(spec);
        return policy;
    }

    private static Attachment attachment(String name, String policyName) {
        Attachment attachment = new Attachment();
        Metadata metadata = new Metadata();
        metadata.setName(name);
        attachment.setMetadata(metadata);
        Attachment.AttachmentSpec spec = new Attachment.AttachmentSpec();
        spec.setPolicyName(policyName);
        attachment.setSpec(spec);
        return attachment;
    }
}
