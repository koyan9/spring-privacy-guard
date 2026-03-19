/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyTenantContextHolderTest {

    @AfterEach
    void clearTenantContext() {
        PrivacyTenantContextHolder.clear();
    }

    @Test
    void openScopeRestoresPreviousTenantAfterClose() {
        PrivacyTenantContextHolder.setTenantId("tenant-a");

        try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope("tenant-b")) {
            assertEquals("tenant-b", PrivacyTenantContextHolder.getTenantId());
        }

        assertEquals("tenant-a", PrivacyTenantContextHolder.getTenantId());
    }

    @Test
    void openScopeClearsTenantWhenBlankValueProvided() {
        PrivacyTenantContextHolder.setTenantId("tenant-a");

        try (PrivacyTenantContextScope ignored = PrivacyTenantContextHolder.openScope("  ")) {
            assertNull(PrivacyTenantContextHolder.getTenantId());
        }

        assertEquals("tenant-a", PrivacyTenantContextHolder.getTenantId());
    }

    @Test
    void snapshotWrapsRunnableAcrossThreadsAndRestoresWorkerState() throws Exception {
        PrivacyTenantContextHolder.setTenantId("tenant-a");
        PrivacyTenantContextSnapshot snapshot = PrivacyTenantContextHolder.snapshot();
        PrivacyTenantContextHolder.clear();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> capturedTenant = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            executor.submit(snapshot.wrap(() -> {
                capturedTenant.set(PrivacyTenantContextHolder.getTenantId());
                latch.countDown();
            }));

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("tenant-a", capturedTenant.get());
            assertNull(executor.submit(PrivacyTenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void snapshotWrapsSupplierForCompletableFuture() throws Exception {
        PrivacyTenantContextHolder.setTenantId("tenant-b");
        PrivacyTenantContextSnapshot snapshot = PrivacyTenantContextHolder.snapshot();
        PrivacyTenantContextHolder.clear();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String tenantId = CompletableFuture
                    .supplyAsync(snapshot.wrap((Supplier<String>) PrivacyTenantContextHolder::getTenantId), executor)
                    .get(5, TimeUnit.SECONDS);

            assertEquals("tenant-b", tenantId);
            assertNull(executor.submit(PrivacyTenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
