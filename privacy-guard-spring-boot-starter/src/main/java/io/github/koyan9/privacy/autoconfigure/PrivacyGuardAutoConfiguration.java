/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.autoconfigure;

import ch.qos.logback.classic.PatternLayout;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.koyan9.privacy.audit.ApplicationEventPrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.CompositePrivacyAuditPublisher;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.JdbcPrivacyAuditRepository;
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
import io.github.koyan9.privacy.audit.RepositoryPrivacyAuditPublisher;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.jackson.PrivacyGuardModule;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcOperations;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(PrivacyGuardProperties.class)
@ConditionalOnProperty(prefix = "privacy.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PrivacyGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MaskingService maskingService(PrivacyGuardProperties properties) {
        return new MaskingService(properties.getFallbackMaskChar());
    }

    @Bean
    @ConditionalOnMissingBean
    public TextMaskingService textMaskingService(MaskingService maskingService) {
        return new TextMaskingService(maskingService);
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
        return new PrivacyLogbackConfigurer(
                privacyLogSanitizer,
                properties.getLogging().getLogback().isInstallTurboFilter(),
                properties.getLogging().getLogback().isBlockUnsafeMessages()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditSchemaLocationResolver privacyAuditSchemaLocationResolver() {
        return new PrivacyAuditSchemaLocationResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "repository-type", havingValue = "IN_MEMORY")
    public InMemoryPrivacyAuditRepository inMemoryPrivacyAuditRepository() {
        return new InMemoryPrivacyAuditRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JdbcOperations.class)
    @ConditionalOnExpression("'${privacy.guard.audit.repository-type:NONE}' == 'JDBC'")
    public PrivacyAuditRepository jdbcPrivacyAuditRepository(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            PrivacyGuardProperties properties
    ) {
        return new JdbcPrivacyAuditRepository(jdbcOperations, objectMapper, properties.getAudit().getJdbc().getTableName());
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
    @ConditionalOnClass(JdbcOperations.class)
    @ConditionalOnExpression("'${privacy.guard.audit.repository-type:NONE}' == 'JDBC' and '${privacy.guard.audit.jdbc.initialize-schema:false}' == 'true'")
    public PrivacyAuditSchemaInitializer privacyAuditSchemaInitializer(
            JdbcOperations jdbcOperations,
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
                properties.getAudit().getJdbc().getTableName()
        );
        initializer.initialize();
        return initializer;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditPublisher privacyAuditPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            ObjectProvider<PrivacyAuditRepository> privacyAuditRepositories
    ) {
        List<PrivacyAuditPublisher> publishers = new ArrayList<>();
        publishers.add(new ApplicationEventPrivacyAuditPublisher(applicationEventPublisher));
        privacyAuditRepositories.orderedStream()
                .map(RepositoryPrivacyAuditPublisher::new)
                .forEach(publishers::add);
        return new CompositePrivacyAuditPublisher(publishers);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditService privacyAuditService(
            PrivacyAuditPublisher privacyAuditPublisher,
            PrivacyLogSanitizer privacyLogSanitizer
    ) {
        return new PrivacyAuditService(privacyAuditPublisher, privacyLogSanitizer);
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
}