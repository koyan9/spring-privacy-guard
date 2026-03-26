/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.InMemoryPrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterEntry;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStats;
import io.github.koyan9.privacy.audit.PrivacyAuditDeadLetterStatsRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditEvent;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryCriteria;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditQueryStats;
import io.github.koyan9.privacy.audit.PrivacyAuditRepository;
import io.github.koyan9.privacy.audit.PrivacyAuditStatsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@Profile("fallback-tenant")
class FallbackTenantRepositoryConfiguration {

    @Bean
    FallbackAuditRepository fallbackAuditRepository() {
        return new FallbackAuditRepository();
    }

    @Bean
    FallbackDeadLetterRepository fallbackDeadLetterRepository() {
        return new FallbackDeadLetterRepository();
    }

    static final class FallbackAuditRepository implements
            PrivacyAuditRepository,
            PrivacyAuditQueryRepository,
            PrivacyAuditStatsRepository {

        private final InMemoryPrivacyAuditRepository delegate = new InMemoryPrivacyAuditRepository();

        @Override
        public void save(PrivacyAuditEvent event) {
            delegate.save(event);
        }

        @Override
        public void saveAll(List<PrivacyAuditEvent> events) {
            delegate.saveAll(events);
        }

        @Override
        public List<PrivacyAuditEvent> findByCriteria(PrivacyAuditQueryCriteria criteria) {
            return delegate.findByCriteria(criteria);
        }

        @Override
        public PrivacyAuditQueryStats computeStats(PrivacyAuditQueryCriteria criteria) {
            return delegate.computeStats(criteria);
        }

        void clear() {
            delegate.clear();
        }
    }

    static final class FallbackDeadLetterRepository implements
            PrivacyAuditDeadLetterRepository,
            PrivacyAuditDeadLetterStatsRepository {

        private final InMemoryPrivacyAuditDeadLetterRepository delegate = new InMemoryPrivacyAuditDeadLetterRepository();

        @Override
        public void save(PrivacyAuditDeadLetterEntry entry) {
            delegate.save(entry);
        }

        @Override
        public void saveAll(List<PrivacyAuditDeadLetterEntry> entries) {
            delegate.saveAll(entries);
        }

        @Override
        public List<PrivacyAuditDeadLetterEntry> findAll() {
            return delegate.findAll();
        }

        @Override
        public List<PrivacyAuditDeadLetterEntry> findByCriteria(PrivacyAuditDeadLetterQueryCriteria criteria) {
            return delegate.findByCriteria(criteria);
        }

        @Override
        public Optional<PrivacyAuditDeadLetterEntry> findById(long id) {
            return delegate.findById(id);
        }

        @Override
        public boolean deleteById(long id) {
            return delegate.deleteById(id);
        }

        @Override
        public PrivacyAuditDeadLetterStats computeStats(PrivacyAuditDeadLetterQueryCriteria criteria) {
            return delegate.computeStats(criteria);
        }

        void clear() {
            delegate.clear();
        }
    }
}
