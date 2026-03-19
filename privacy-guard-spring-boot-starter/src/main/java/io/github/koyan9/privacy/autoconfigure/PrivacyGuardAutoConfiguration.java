/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import ch.qos.logback.classic.PatternLayout;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.ApplicationEventPrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.AsyncPrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.BufferedPrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.CompositePrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.LoggingPrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterSchemaLocationResolver;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterService;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStats;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsService;
import io.github.koyan9.privacy.audit.PrivacyAuditEventLogger;
import io.github.koyan9.privacy.audit.PrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryStats;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaInitializer;
import io.github.koyan9.privacy.audit.PrivacyAuditSchemaLocationResolver;
import io.github.koyan9.privacy.audit.PrivacyAuditService;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterOperationsService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditQueryService;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditReadRepository;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditPolicy;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditPolicyResolver;
import io.github.koyan9.privacy.audit.PrivacyTenantAuditDeadLetterReadRepository;
import io.github.koyan9.privacy.audit.RepositoryBackedPrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.RepositoryPrivacyAuditPublisher;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.MaskingStrategy;
import io.github.koyan9.privacy.core.PrivacyTenantContextHolder;
import io.github.koyan9.privacy.core.PrivacyTenantPolicy;
import io.github.koyan9.privacy.core.PrivacyTenantPolicyResolver;
import io.github.koyan9.privacy.core.PrivacyTenantProvider;
import io.github.koyan9.privacy.core.SensitiveType;
import io.github.koyan9.privacy.core.TextMaskingRule;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.jackson.PrivacyGuardModule;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackConfigurer;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackSanitizerSettings;
import io.github.koyan9.privacy.tenant.PrivacyTenantContextFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfiguration
@EnableConfigurationProperties(PrivacyGuardProperties.class)
@ConditionalOnProperty(prefix = "privacy.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PrivacyGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PrivacyTenantProvider privacyTenantProvider(PrivacyGuardProperties properties) {
        return () -> {
            String tenantId = PrivacyTenantContextHolder.getTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId;
            }
            if (!properties.getTenant().isEnabled()) {
                return null;
            }
            String defaultTenant = properties.getTenant().getDefaultTenant();
            return defaultTenant == null || defaultTenant.isBlank() ? null : defaultTenant.trim();
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyTenantPolicyResolver privacyTenantPolicyResolver(PrivacyGuardProperties properties) {
        Map<String, PrivacyTenantPolicy> tenantPolicies = buildTenantPolicies(properties.getTenant());
        return tenantId -> {
            if (tenantId == null || tenantId.isBlank()) {
                return PrivacyTenantPolicy.none();
            }
            return tenantPolicies.getOrDefault(tenantId.trim(), PrivacyTenantPolicy.none());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyTenantAuditPolicyResolver privacyTenantAuditPolicyResolver(PrivacyGuardProperties properties) {
        Map<String, PrivacyTenantAuditPolicy> tenantAuditPolicies = buildTenantAuditPolicies(properties.getTenant());
        return tenantId -> {
            if (tenantId == null || tenantId.isBlank()) {
                return PrivacyTenantAuditPolicy.none();
            }
            return tenantAuditPolicies.getOrDefault(tenantId.trim(), PrivacyTenantAuditPolicy.none());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public MaskingService maskingService(
            PrivacyGuardProperties properties,
            ObjectProvider<MaskingStrategy> maskingStrategies,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantPolicyResolver tenantPolicyResolver
    ) {
        return new MaskingService(
                properties.getFallbackMaskChar(),
                maskingStrategies.orderedStream().toList(),
                tenantProvider,
                tenantPolicyResolver
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TextMaskingService textMaskingService(
            MaskingService maskingService,
            PrivacyGuardProperties properties,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantPolicyResolver tenantPolicyResolver
    ) {
        List<TextMaskingRule> rules = buildTextMaskingRules(properties.getMasking());
        return new TextMaskingService(maskingService, rules, tenantProvider, tenantPolicyResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyLogSanitizer privacyLogSanitizer(TextMaskingService textMaskingService) {
        return new PrivacyLogSanitizer(textMaskingService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyLoggerFactory privacyLoggerFactory(PrivacyLogSanitizer privacyLogSanitizer) {
        return new PrivacyLoggerFactory(privacyLogSanitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(PatternLayout.class)
    @ConditionalOnProperty(prefix = "privacy.guard.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyLogbackConfigurer privacyLogbackConfigurer(PrivacyLogSanitizer privacyLogSanitizer, PrivacyGuardProperties properties) {
        PrivacyGuardProperties.Logging logging = properties.getLogging();
        PrivacyLogbackSanitizerSettings settings = new PrivacyLogbackSanitizerSettings(
                logging.getMdc().isEnabled(),
                new java.util.LinkedHashSet<>(logging.getMdc().getIncludeKeys()),
                new java.util.LinkedHashSet<>(logging.getMdc().getExcludeKeys()),
                logging.getStructured().isEnabled(),
                new java.util.LinkedHashSet<>(logging.getStructured().getIncludeKeys()),
                new java.util.LinkedHashSet<>(logging.getStructured().getExcludeKeys())
        );
        return new PrivacyLogbackConfigurer(
                privacyLogSanitizer,
                properties.getLogging().getLogback().isInstallTurboFilter(),
                properties.getLogging().getLogback().isBlockUnsafeMessages(),
                settings
        );
    }

    private List<TextMaskingRule> buildTextMaskingRules(PrivacyGuardProperties.Masking masking) {
        if (masking == null || masking.getText() == null) {
            return TextMaskingRule.defaults();
        }
        return buildTextMaskingRules(masking.getText());
    }

    private List<TextMaskingRule> buildTextMaskingRules(PrivacyGuardProperties.Masking.Text text) {
        if (text == null) {
            return TextMaskingRule.defaults();
        }
        EnumMap<SensitiveType, java.util.Optional<java.util.regex.Pattern>> overrides = new EnumMap<>(SensitiveType.class);
        applyOverride(overrides, SensitiveType.EMAIL, text.getEmailPattern());
        applyOverride(overrides, SensitiveType.PHONE, text.getPhonePattern());
        applyOverride(overrides, SensitiveType.ID_CARD, text.getIdCardPattern());

        List<TextMaskingRule> rules = new ArrayList<>();
        for (TextMaskingRule rule : TextMaskingRule.defaults()) {
            java.util.Optional<java.util.regex.Pattern> override = overrides.get(rule.sensitiveType());
            if (override == null) {
                rules.add(rule);
            } else if (override.isPresent()) {
                rules.add(new TextMaskingRule(rule.sensitiveType(), override.get()));
            }
        }

        if (text.getAdditionalPatterns() != null) {
            for (PrivacyGuardProperties.Masking.AdditionalPattern additional : text.getAdditionalPatterns()) {
                if (additional == null) {
                    continue;
                }
                String pattern = additional.getPattern();
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                rules.add(new TextMaskingRule(additional.getType(), java.util.regex.Pattern.compile(pattern)));
            }
        }
        return rules;
    }

    private Map<String, PrivacyTenantPolicy> buildTenantPolicies(PrivacyGuardProperties.Tenant tenant) {
        if (tenant == null || !tenant.isEnabled() || tenant.getPolicies().isEmpty()) {
            return Map.of();
        }
        Map<String, PrivacyTenantPolicy> tenantPolicies = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, PrivacyGuardProperties.TenantPolicy> entry : tenant.getPolicies().entrySet()) {
            String tenantId = entry.getKey();
            PrivacyGuardProperties.TenantPolicy tenantPolicy = entry.getValue();
            if (tenantId == null || tenantId.isBlank() || tenantPolicy == null) {
                continue;
            }
            tenantPolicies.put(
                    tenantId.trim(),
                    new PrivacyTenantPolicy(
                            tenantPolicy.getFallbackMaskChar(),
                            buildTextMaskingRules(tenantPolicy.getText())
                    )
            );
        }
        return Map.copyOf(tenantPolicies);
    }

    private Map<String, PrivacyTenantAuditPolicy> buildTenantAuditPolicies(PrivacyGuardProperties.Tenant tenant) {
        if (tenant == null || !tenant.isEnabled() || tenant.getPolicies().isEmpty()) {
            return Map.of();
        }
        Map<String, PrivacyTenantAuditPolicy> tenantAuditPolicies = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, PrivacyGuardProperties.TenantPolicy> entry : tenant.getPolicies().entrySet()) {
            String tenantId = entry.getKey();
            PrivacyGuardProperties.TenantPolicy tenantPolicy = entry.getValue();
            if (tenantId == null || tenantId.isBlank() || tenantPolicy == null) {
                continue;
            }
            PrivacyGuardProperties.TenantAudit audit = tenantPolicy.getAudit();
            tenantAuditPolicies.put(
                    tenantId.trim(),
                    new PrivacyTenantAuditPolicy(
                            new java.util.LinkedHashSet<>(audit.getIncludeDetailKeys()),
                            new java.util.LinkedHashSet<>(audit.getExcludeDetailKeys()),
                            audit.isAttachTenantId(),
                            audit.getTenantDetailKey()
                    )
            );
        }
        return Map.copyOf(tenantAuditPolicies);
    }

    private void applyOverride(
            EnumMap<SensitiveType, java.util.Optional<java.util.regex.Pattern>> overrides,
            SensitiveType type,
            String pattern
    ) {
        if (pattern == null) {
            return;
        }
        if (pattern.isBlank()) {
            overrides.put(type, java.util.Optional.empty());
            return;
        }
        overrides.put(type, java.util.Optional.of(java.util.regex.Pattern.compile(pattern)));
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditSchemaLocationResolver privacyAuditSchemaLocationResolver() {
        return new PrivacyAuditSchemaLocationResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterSchemaLocationResolver privacyAuditDeadLetterSchemaLocationResolver() {
        return new PrivacyAuditDeadLetterSchemaLocationResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterHandler privacyAuditDeadLetterHandler(
            ObjectProvider<PrivacyAuditDeadLetterRepository> repositories,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        PrivacyAuditDeadLetterRepository repository = repositories.getIfAvailable();
        if (repository != null) {
            return new RepositoryBackedPrivacyAuditDeadLetterHandler(
                    repository,
                    tenantProvider,
                    tenantAuditPolicyResolver
            );
        }
        return new LoggingPrivacyAuditDeadLetterHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "repository-type", havingValue = "IN_MEMORY")
    public InMemoryPrivacyAuditRepository inMemoryPrivacyAuditRepository() {
        return new InMemoryPrivacyAuditRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit.dead-letter", name = "repository-type", havingValue = "IN_MEMORY")
    public InMemoryPrivacyAuditDeadLetterRepository inMemoryPrivacyAuditDeadLetterRepository() {
        return new InMemoryPrivacyAuditDeadLetterRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditQueryService privacyAuditQueryService(ObjectProvider<PrivacyAuditQueryRepository> queryRepositories) {
        PrivacyAuditQueryRepository queryRepository = queryRepositories.getIfAvailable(() -> criteria -> List.of());
        return new PrivacyAuditQueryService(queryRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditStatsService privacyAuditStatsService(ObjectProvider<PrivacyAuditStatsRepository> statsRepositories) {
        PrivacyAuditStatsRepository statsRepository = statsRepositories.getIfAvailable(() -> criteria -> new PrivacyAuditQueryStats(0, Map.of(), Map.of(), Map.of()));
        return new PrivacyAuditStatsService(statsRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyTenantAuditQueryService privacyTenantAuditQueryService(
            PrivacyAuditQueryService privacyAuditQueryService,
            PrivacyAuditStatsService privacyAuditStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            ObjectProvider<PrivacyTenantAuditReadRepository> tenantAuditReadRepositories
    ) {
        return new PrivacyTenantAuditQueryService(
                privacyAuditQueryService,
                privacyAuditStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantAuditReadRepositories.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PrivacyAuditDeadLetterRepository.class)
    public PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver,
            ObjectProvider<PrivacyTenantAuditDeadLetterReadRepository> tenantAuditDeadLetterReadRepositories
    ) {
        return new PrivacyTenantAuditDeadLetterQueryService(
                privacyAuditDeadLetterService,
                privacyAuditDeadLetterStatsService,
                tenantProvider,
                tenantAuditPolicyResolver,
                tenantAuditDeadLetterReadRepositories.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PrivacyAuditDeadLetterRepository.class)
    public PrivacyTenantAuditDeadLetterOperationsService privacyTenantAuditDeadLetterOperationsService(
            PrivacyAuditDeadLetterService privacyAuditDeadLetterService,
            PrivacyTenantAuditDeadLetterQueryService privacyTenantAuditDeadLetterQueryService,
            PrivacyTenantProvider tenantProvider
    ) {
        return new PrivacyTenantAuditDeadLetterOperationsService(
                privacyAuditDeadLetterService,
                privacyTenantAuditDeadLetterQueryService,
                tenantProvider
        );
    }

    @Bean(name = "privacyAuditReplayPublisher")
    @ConditionalOnMissingBean(name = "privacyAuditReplayPublisher")
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditPublisher privacyAuditReplayPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            ObjectProvider<PrivacyAuditRepository> privacyAuditRepositories,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        List<PrivacyAuditPublisher> publishers = new ArrayList<>();
        publishers.add(new ApplicationEventPrivacyAuditPublisher(applicationEventPublisher));
        privacyAuditRepositories.orderedStream()
                .map(repository -> new RepositoryPrivacyAuditPublisher(repository, tenantProvider, tenantAuditPolicyResolver))
                .forEach(publishers::add);
        return new CompositePrivacyAuditPublisher(publishers);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PrivacyAuditDeadLetterRepository.class)
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditDeadLetterService privacyAuditDeadLetterService(
            PrivacyAuditDeadLetterRepository deadLetterRepository,
            @Qualifier("privacyAuditReplayPublisher") PrivacyAuditPublisher replayPublisher
    ) {
        return new PrivacyAuditDeadLetterService(deadLetterRepository, replayPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PrivacyAuditDeadLetterRepository.class)
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditDeadLetterStatsService privacyAuditDeadLetterStatsService(ObjectProvider<PrivacyAuditDeadLetterStatsRepository> repositories) {
        PrivacyAuditDeadLetterStatsRepository repository = repositories.getIfAvailable(() -> criteria -> new PrivacyAuditDeadLetterStats(0, Map.of(), Map.of(), Map.of(), Map.of()));
        return new PrivacyAuditDeadLetterStatsService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditPublisher privacyAuditPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            ObjectProvider<PrivacyAuditRepository> privacyAuditRepositories,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            @Qualifier("privacyAuditExecutor") ObjectProvider<ScheduledExecutorService> privacyAuditExecutorProvider,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        List<PrivacyAuditPublisher> publishers = new ArrayList<>();
        publishers.add(maybeAsync(
                new ApplicationEventPrivacyAuditPublisher(applicationEventPublisher),
                properties,
                deadLetterHandler,
                privacyAuditExecutorProvider
        ));
        privacyAuditRepositories.orderedStream()
                .map(repository -> repositoryPublisher(
                        repository,
                        properties,
                        deadLetterHandler,
                        privacyAuditExecutorProvider,
                        tenantProvider,
                        tenantAuditPolicyResolver
                ))
                .forEach(publishers::add);
        return new CompositePrivacyAuditPublisher(publishers);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditService privacyAuditService(
            PrivacyAuditPublisher privacyAuditPublisher,
            PrivacyLogSanitizer privacyLogSanitizer,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        return new PrivacyAuditService(
                privacyAuditPublisher,
                privacyLogSanitizer,
                tenantProvider,
                tenantAuditPolicyResolver
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = {"enabled", "log-events"}, havingValue = "true", matchIfMissing = true)
    public PrivacyAuditEventLogger privacyAuditEventLogger() {
        return new PrivacyAuditEventLogger();
    }

    @Bean(name = "privacyGuardJacksonModule")
    @ConditionalOnMissingBean(name = "privacyGuardJacksonModule")
    @ConditionalOnClass(ObjectMapper.class)
    public Module privacyGuardJacksonModule(MaskingService maskingService) {
        return new PrivacyGuardModule(maskingService);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "privacy.guard.tenant", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public PrivacyTenantContextFilter privacyTenantContextFilter(PrivacyGuardProperties properties) {
        return new PrivacyTenantContextFilter(
                properties.getTenant().getHeaderName(),
                properties.getTenant().getDefaultTenant()
        );
    }

    private PrivacyAuditPublisher repositoryPublisher(
            PrivacyAuditRepository repository,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            ObjectProvider<ScheduledExecutorService> privacyAuditExecutorProvider,
            PrivacyTenantProvider tenantProvider,
            PrivacyTenantAuditPolicyResolver tenantAuditPolicyResolver
    ) {
        if (properties.getAudit().getBatch().isEnabled()) {
            return new BufferedPrivacyAuditPublisher(
                    repository,
                    privacyAuditExecutorProvider.getObject(),
                    properties.getAudit().getBatch().getSize(),
                    properties.getAudit().getBatch().getFlushInterval(),
                    properties.getAudit().getRetry().getMaxAttempts(),
                    properties.getAudit().getRetry().getBackoff(),
                    deadLetterHandler,
                    tenantProvider,
                    tenantAuditPolicyResolver
            );
        }
        return maybeAsync(
                new RepositoryPrivacyAuditPublisher(repository, tenantProvider, tenantAuditPolicyResolver),
                properties,
                deadLetterHandler,
                privacyAuditExecutorProvider
        );
    }

    private PrivacyAuditPublisher maybeAsync(
            PrivacyAuditPublisher delegate,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            ObjectProvider<ScheduledExecutorService> privacyAuditExecutorProvider
    ) {
        if (!properties.getAudit().getAsync().isEnabled()) {
            return delegate;
        }
        return new AsyncPrivacyAuditPublisher(
                delegate,
                privacyAuditExecutorProvider.getObject(),
                properties.getAudit().getRetry().getMaxAttempts(),
                properties.getAudit().getRetry().getBackoff(),
                deadLetterHandler
        );
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcOperations")
    static class JdbcAuditConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnExpression("'${privacy.guard.audit.repository-type:NONE}' == 'JDBC'")
        public PrivacyAuditRepository jdbcPrivacyAuditRepository(
                org.springframework.jdbc.core.JdbcOperations jdbcOperations,
                ObjectMapper objectMapper,
                PrivacyGuardProperties properties
        ) {
            return new JdbcPrivacyAuditRepository(
                    jdbcOperations,
                    objectMapper,
                    properties.getAudit().getJdbc().getTableName(),
                    properties.getAudit().getJdbc().getTenantColumnName(),
                    properties.getAudit().getJdbc().getTenantDetailKey()
            );
        }

        @Bean
        @ConditionalOnExpression("'${privacy.guard.audit.repository-type:NONE}' == 'JDBC' and '${privacy.guard.audit.jdbc.initialize-schema:false}' == 'true'")
        public PrivacyAuditSchemaInitializer privacyAuditSchemaInitializer(
                org.springframework.jdbc.core.JdbcOperations jdbcOperations,
                ResourceLoader resourceLoader,
                ObjectProvider<DataSource> dataSourceProvider,
                PrivacyAuditSchemaLocationResolver schemaLocationResolver,
                PrivacyGuardProperties properties
        ) {
            String schemaLocation = schemaLocationResolver.resolve(properties.getAudit().getJdbc(), dataSourceProvider.getIfAvailable());
            PrivacyAuditSchemaInitializer initializer = new PrivacyAuditSchemaInitializer(
                    jdbcOperations,
                    resourceLoader,
                    schemaLocation,
                    properties.getAudit().getJdbc().getTableName(),
                    properties.getAudit().getJdbc().getTenantColumnName()
            );
            initializer.initialize();
            return initializer;
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.repository-type:NONE}' == 'JDBC'")
        public PrivacyAuditDeadLetterRepository jdbcPrivacyAuditDeadLetterRepository(
                org.springframework.jdbc.core.JdbcOperations jdbcOperations,
                ObjectMapper objectMapper,
                PrivacyGuardProperties properties
        ) {
            return new JdbcPrivacyAuditDeadLetterRepository(
                    jdbcOperations,
                    objectMapper,
                    properties.getAudit().getDeadLetter().getJdbc().getTableName(),
                    properties.getAudit().getDeadLetter().getJdbc().getTenantColumnName(),
                    properties.getAudit().getDeadLetter().getJdbc().getTenantDetailKey()
            );
        }

        @Bean
        @ConditionalOnExpression("'${privacy.guard.audit.dead-letter.repository-type:NONE}' == 'JDBC' and '${privacy.guard.audit.dead-letter.jdbc.initialize-schema:false}' == 'true'")
        public PrivacyAuditSchemaInitializer privacyAuditDeadLetterSchemaInitializer(
                org.springframework.jdbc.core.JdbcOperations jdbcOperations,
                ResourceLoader resourceLoader,
                ObjectProvider<DataSource> dataSourceProvider,
                PrivacyAuditDeadLetterSchemaLocationResolver schemaLocationResolver,
                PrivacyGuardProperties properties
        ) {
            String schemaLocation = schemaLocationResolver.resolve(properties.getAudit().getDeadLetter().getJdbc(), dataSourceProvider.getIfAvailable());
            PrivacyAuditSchemaInitializer initializer = new PrivacyAuditSchemaInitializer(
                    jdbcOperations,
                    resourceLoader,
                    schemaLocation,
                    properties.getAudit().getDeadLetter().getJdbc().getTableName(),
                    properties.getAudit().getDeadLetter().getJdbc().getTenantColumnName()
            );
            initializer.initialize();
            return initializer;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("'${privacy.guard.audit.async.enabled:false}' == 'true' or '${privacy.guard.audit.batch.enabled:false}' == 'true'")
    static class AuditExecutionConfiguration {

        @Bean(name = "privacyAuditExecutor", destroyMethod = "shutdown")
        @ConditionalOnMissingBean(name = "privacyAuditExecutor")
        ScheduledExecutorService privacyAuditExecutor(PrivacyGuardProperties properties) {
            PrivacyGuardProperties.Async async = properties.getAudit().getAsync();
            String prefix = async.getThreadNamePrefix();
            int poolSize = Math.max(1, async.getThreadPoolSize());
            AtomicInteger threadCounter = new AtomicInteger();
            ThreadFactory threadFactory = runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName(prefix + threadCounter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            return Executors.newScheduledThreadPool(poolSize, threadFactory);
        }
    }
}
