package com.xhhao.insight.halo;

import static run.halo.app.extension.index.query.Queries.equal;

import com.xhhao.insight.halo.AttachmentUrlAccessProbe.Outcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.PolicyTemplate;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalLinkProcessor;

/**
 * Verifies attachment policies without creating or modifying remote objects.
 *
 * <p>An existing attachment is used as the read-only probe sample. Policies without an
 * existing attachment remain explicitly inconclusive instead of uploading a synthetic file.
 */
@Component
public final class AttachmentStorageProbe {

    static final int MAX_POLICIES = 16;
    static final Duration SHARED_URL_TTL = Duration.ofMinutes(1);
    static final Duration POLICY_TIMEOUT = Duration.ofSeconds(6);
    static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(10);

    private final ReactiveExtensionClient extensionClient;
    private final AttachmentService attachmentService;
    private final ExternalLinkProcessor externalLinkProcessor;
    private final AttachmentUrlAccessProbe urlAccessProbe;

    public AttachmentStorageProbe(
        ReactiveExtensionClient extensionClient,
        AttachmentService attachmentService,
        ExternalLinkProcessor externalLinkProcessor,
        AttachmentUrlAccessProbe urlAccessProbe
    ) {
        this.extensionClient = extensionClient;
        this.attachmentService = attachmentService;
        this.externalLinkProcessor = externalLinkProcessor;
        this.urlAccessProbe = urlAccessProbe;
    }

    public Mono<DependencyStatus> probe() {
        return extensionClient.listAll(Policy.class, ListOptions.builder().build(), Sort.unsorted())
            .take(MAX_POLICIES + 1L)
            .collectList()
            .flatMap(this::probePolicies)
            .timeout(TOTAL_TIMEOUT, Mono.just(DependencyStatus.unknown(
                "attachments", "附件存储", "只读附件存储探测超过 10 秒，结果未知")))
            .onErrorResume(error -> Mono.just(DependencyStatus.warning(
                "attachments", "附件存储", "无法读取或探测附件存储策略")));
    }

    private Mono<DependencyStatus> probePolicies(List<Policy> discovered) {
        if (discovered.isEmpty()) {
            return Mono.just(DependencyStatus.warning(
                "attachments", "附件存储", "未配置存储策略"));
        }

        boolean truncated = discovered.size() > MAX_POLICIES;
        List<Policy> policies = new ArrayList<>(
            discovered.subList(0, Math.min(discovered.size(), MAX_POLICIES)));
        policies.sort(Comparator.comparing(AttachmentStorageProbe::policyName,
            String.CASE_INSENSITIVE_ORDER));

        return Flux.fromIterable(policies)
            .flatMapSequential(this::probePolicy, 4)
            .collectList()
            .map(results -> summarize(results, truncated));
    }

    private Mono<PolicyCheck> probePolicy(Policy policy) {
        String name = policyName(policy);
        if (!StringUtils.hasText(metadataName(policy))) {
            return Mono.just(PolicyCheck.unavailable(name, "策略缺少资源名称"));
        }
        Policy.PolicySpec spec = policy.getSpec();
        if (spec == null) {
            return Mono.just(PolicyCheck.unavailable(name, "策略缺少配置"));
        }
        if (!StringUtils.hasText(spec.getTemplateName())) {
            return Mono.just(PolicyCheck.unavailable(name, "策略未关联存储模板"));
        }
        if (!StringUtils.hasText(spec.getConfigMapName())) {
            return Mono.just(PolicyCheck.unavailable(name, "策略未关联配置数据"));
        }

        Mono<Boolean> templateExists = extensionClient
            .fetch(PolicyTemplate.class, spec.getTemplateName())
            .hasElement();
        Mono<Boolean> configExists = extensionClient
            .fetch(ConfigMap.class, spec.getConfigMapName())
            .hasElement();

        return Mono.zip(templateExists, configExists)
            .flatMap(exists -> {
                if (!exists.getT1()) {
                    return Mono.just(PolicyCheck.unavailable(name, "关联的存储模板不存在"));
                }
                if (!exists.getT2()) {
                    return Mono.just(PolicyCheck.unavailable(name, "关联的存储配置不存在"));
                }
                return probeExistingAttachment(policy, name);
            })
            .timeout(POLICY_TIMEOUT)
            .onErrorResume(error -> Mono.just(PolicyCheck.unavailable(
                name, "策略只读探测失败（" + simpleFailureName(error) + "）")));
    }

    private Mono<PolicyCheck> probeExistingAttachment(Policy policy, String name) {
        ListOptions attachmentQuery = ListOptions.builder()
            .fieldQuery(equal("spec.policyName", metadataName(policy)))
            .build();
        Sort newestFirst = Sort.by(Sort.Order.desc("metadata.creationTimestamp"));

        return extensionClient.listAll(Attachment.class, attachmentQuery, newestFirst)
            .next()
            .flatMap(attachment -> resolveAccessUri(attachment)
                .flatMap(externalLinkProcessor::processLink)
                .flatMap(urlAccessProbe::probe)
                .map(result -> switch (result.outcome()) {
                    case REACHABLE -> PolicyCheck.reachable(name, result.detail());
                    case INCONCLUSIVE -> PolicyCheck.inconclusive(name, result.detail());
                    case UNREACHABLE -> PolicyCheck.unavailable(name, result.detail());
                })
                .switchIfEmpty(Mono.just(PolicyCheck.unavailable(
                    name, "已存在附件，但对应处理器未返回访问地址"))))
            .switchIfEmpty(Mono.just(PolicyCheck.inconclusive(
                name, "没有现有附件可供只读探测（不会上传测试文件）")));
    }

    private Mono<java.net.URI> resolveAccessUri(Attachment attachment) {
        return attachmentService.getSharedURL(attachment, SHARED_URL_TTL)
            .switchIfEmpty(Mono.defer(() -> attachmentService.getPermalink(attachment)));
    }

    private static DependencyStatus summarize(List<PolicyCheck> checks, boolean truncated) {
        long reachable = checks.stream().filter(check -> check.outcome() == Outcome.REACHABLE).count();
        long inconclusive = checks.stream()
            .filter(check -> check.outcome() == Outcome.INCONCLUSIVE)
            .count();
        long unavailable = checks.stream()
            .filter(check -> check.outcome() == Outcome.UNREACHABLE)
            .count();

        String counts = "已检查 " + checks.size() + " 个策略："
            + reachable + " 个可达，" + inconclusive + " 个受限，"
            + unavailable + " 个异常";
        if (truncated) {
            counts += "；策略超过 " + MAX_POLICIES + " 个，仅探测前 " + MAX_POLICIES + " 个";
        }

        if (unavailable > 0) {
            return DependencyStatus.warning("attachments", "附件存储",
                counts + "；" + firstDetail(checks, Outcome.UNREACHABLE));
        }
        if (inconclusive > 0 || truncated) {
            return DependencyStatus.unknown("attachments", "附件存储",
                counts + (inconclusive > 0
                    ? "；" + firstDetail(checks, Outcome.INCONCLUSIVE)
                    : ""));
        }
        return DependencyStatus.healthy("attachments", "附件存储", counts);
    }

    private static String firstDetail(List<PolicyCheck> checks, Outcome outcome) {
        return checks.stream()
            .filter(check -> check.outcome() == outcome)
            .findFirst()
            .map(check -> "策略“" + check.name() + "”：" + check.detail())
            .orElse("");
    }

    private static String policyName(Policy policy) {
        if (policy.getSpec() != null && StringUtils.hasText(policy.getSpec().getDisplayName())) {
            return policy.getSpec().getDisplayName();
        }
        String metadataName = metadataName(policy);
        return StringUtils.hasText(metadataName) ? metadataName : "未命名策略";
    }

    private static String metadataName(Policy policy) {
        return policy.getMetadata() == null ? null : policy.getMetadata().getName();
    }

    private static String simpleFailureName(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private record PolicyCheck(String name, Outcome outcome, String detail) {

        static PolicyCheck reachable(String name, String detail) {
            return new PolicyCheck(name, Outcome.REACHABLE, detail);
        }

        static PolicyCheck inconclusive(String name, String detail) {
            return new PolicyCheck(name, Outcome.INCONCLUSIVE, detail);
        }

        static PolicyCheck unavailable(String name, String detail) {
            return new PolicyCheck(name, Outcome.UNREACHABLE, detail);
        }
    }
}
