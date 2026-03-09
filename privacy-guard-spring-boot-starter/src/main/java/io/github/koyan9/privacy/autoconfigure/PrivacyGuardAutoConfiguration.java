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
import io.github.koyan9.privacy.audit.RepositoryBackedPrivacyAuditDeadLetterHandler;
import io.github.koyan9.privacy.audit.RepositoryPrivacyAuditPublisher;
import io.github.koyan9.privacy.core.MaskingService;
import io.github.koyan9.privacy.core.MaskingStrategy;
import io.github.koyan9.privacy.core.TextMaskingService;
import io.github.koyan9.privacy.jackson.PrivacyGuardModule;
import io.github.koyan9.privacy.logging.PrivacyLogSanitizer;
import io.github.koyan9.privacy.logging.PrivacyLoggerFactory;
import io.github.koyan9.privacy.logging.logback.PrivacyLogbackConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.ArrayList;
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
    public MaskingService maskingService(PrivacyGuardProperties properties, ObjectProvider<MaskingStrategy> maskingStrategies) {
        return new MaskingService(properties.getFallbackMaskChar(), maskingStrategies.orderedStream().toList());
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
    public PrivacyAuditDeadLetterSchemaLocationResolver privacyAuditDeadLetterSchemaLocationResolver() {
        return new PrivacyAuditDeadLetterSchemaLocationResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrivacyAuditDeadLetterHandler privacyAuditDeadLetterHandler(ObjectProvider<PrivacyAuditDeadLetterRepository> repositories) {
        PrivacyAuditDeadLetterRepository repository = repositories.getIfAvailable();
        if (repository != null) {
            return new RepositoryBackedPrivacyAuditDeadLetterHandler(repository);
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

    @Bean(name = "privacyAuditReplayPublisher")
    @ConditionalOnMissingBean(name = "privacyAuditReplayPublisher")
    @ConditionalOnProperty(prefix = "privacy.guard.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PrivacyAuditPublisher privacyAuditReplayPublisher(
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
            @Qualifier("privacyAuditExecutor") ObjectProvider<ScheduledExecutorService> privacyAuditExecutorProvider
    ) {
        List<PrivacyAuditPublisher> publishers = new ArrayList<>();
        publishers.add(maybeAsync(
                new ApplicationEventPrivacyAuditPublisher(applicationEventPublisher),
                properties,
                deadLetterHandler,
                privacyAuditExecutorProvider
        ));
        privacyAuditRepositories.orderedStream()
                .map(repository -> repositoryPublisher(repository, properties, deadLetterHandler, privacyAuditExecutorProvider))
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

    private PrivacyAuditPublisher repositoryPublisher(
            PrivacyAuditRepository repository,
            PrivacyGuardProperties properties,
            PrivacyAuditDeadLetterHandler deadLetterHandler,
            ObjectProvider<ScheduledExecutorService> privacyAuditExecutorProvider
    ) {
        if (properties.getAudit().getBatch().isEnabled()) {
            return new BufferedPrivacyAuditPublisher(
                    repository,
                    privacyAuditExecutorProvider.getObject(),
                    properties.getAudit().getBatch().getSize(),
                    properties.getAudit().getBatch().getFlushInterval(),
                    properties.getAudit().getRetry().getMaxAttempts(),
                    properties.getAudit().getRetry().getBackoff(),
                    deadLetterHandler
            );
        }
        return maybeAsync(new RepositoryPrivacyAuditPublisher(repository), properties, deadLetterHandler, privacyAuditExecutorProvider);
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
            return new JdbcPrivacyAuditRepository(jdbcOperations, objectMapper, properties.getAudit().getJdbc().getTableName());
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
                    properties.getAudit().getJdbc().getTableName()
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
                    properties.getAudit().getDeadLetter().getJdbc().getTableName()
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
                    properties.getAudit().getDeadLetter().getJdbc().getTableName()
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
            String prefix = properties.getAudit().getAsync().getThreadNamePrefix();
            AtomicInteger threadCounter = new AtomicInteger();
            ThreadFactory threadFactory = runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName(prefix + threadCounter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            return Executors.newSingleThreadScheduledExecutor(threadFactory);
        }
    }
}
