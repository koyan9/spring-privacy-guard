/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@StableSpi
public record PrivacyTenantContextSnapshot(String tenantId) {

    public PrivacyTenantContextSnapshot {
        tenantId = PrivacyTenantContextHolder.normalize(tenantId);
    }

    public static PrivacyTenantContextSnapshot capture() {
        return new PrivacyTenantContextSnapshot(PrivacyTenantContextHolder.getTenantId());
    }

    public PrivacyTenantContextScope openScope() {
        return PrivacyTenantContextHolder.openScope(tenantId);
    }

    public Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        return () -> run(task);
    }

    public <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        return () -> call(task);
    }

    public <T> Supplier<T> wrap(Supplier<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        return () -> supply(task);
    }

    public void run(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            task.run();
        }
    }

    public <T> T call(Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            return task.call();
        }
    }

    public <T> T supply(Supplier<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            return task.get();
        }
    }
}
