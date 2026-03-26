/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
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

    @Test
    void snapshotWrapsExecutorAndCallbackTypesForCompletableFutureStages() throws Exception {
        PrivacyTenantContextHolder.setTenantId("tenant-c");
        PrivacyTenantContextSnapshot snapshot = PrivacyTenantContextHolder.snapshot();
        PrivacyTenantContextHolder.clear();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Executor tenantAwareExecutor = snapshot.wrap((Executor) executor);
            AtomicReference<String> acceptedValue = new AtomicReference<>();
            AtomicReference<String> completionValue = new AtomicReference<>();

            String result = CompletableFuture
                    .completedFuture("alpha")
                    .thenApplyAsync(snapshot.wrap((Function<String, String>) value ->
                            PrivacyTenantContextHolder.getTenantId() + ":" + value), tenantAwareExecutor)
                    .thenCombineAsync(
                            CompletableFuture.completedFuture("beta"),
                            snapshot.wrap((BiFunction<String, String, String>) (left, right) ->
                                    PrivacyTenantContextHolder.getTenantId() + ":" + left + ":" + right),
                            tenantAwareExecutor
                    )
                    .thenApplyAsync(snapshot.wrap((Function<String, String>) value -> value + ":done"), tenantAwareExecutor)
                    .get(5, TimeUnit.SECONDS);

            CompletableFuture.completedFuture("accepted")
                    .thenAcceptAsync(snapshot.wrap((Consumer<String>) value ->
                            acceptedValue.set(PrivacyTenantContextHolder.getTenantId() + ":" + value)), tenantAwareExecutor)
                    .get(5, TimeUnit.SECONDS);

            CompletableFuture.completedFuture("complete")
                    .whenCompleteAsync(snapshot.wrap((BiConsumer<String, Throwable>) (value, error) ->
                            completionValue.set(PrivacyTenantContextHolder.getTenantId() + ":" + value + ":" + error)), tenantAwareExecutor)
                    .get(5, TimeUnit.SECONDS);

            assertEquals("tenant-c:tenant-c:alpha:beta:done", result);
            assertEquals("tenant-c:accepted", acceptedValue.get());
            assertEquals("tenant-c:complete:null", completionValue.get());
            assertNull(executor.submit(PrivacyTenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
