/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.privacy.core;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
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

    public <T> Consumer<T> wrap(Consumer<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        return value -> accept(task, value);
    }

    public <T, R> Function<T, R> wrap(Function<T, R> task) {
        Objects.requireNonNull(task, "task must not be null");
        return value -> apply(task, value);
    }

    public <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> task) {
        Objects.requireNonNull(task, "task must not be null");
        return (left, right) -> accept(task, left, right);
    }

    public <T, U, R> BiFunction<T, U, R> wrap(BiFunction<T, U, R> task) {
        Objects.requireNonNull(task, "task must not be null");
        return (left, right) -> apply(task, left, right);
    }

    public Executor wrap(Executor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        return command -> executor.execute(wrap(command));
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

    public <T> void accept(Consumer<T> task, T value) {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            task.accept(value);
        }
    }

    public <T, R> R apply(Function<T, R> task, T value) {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            return task.apply(value);
        }
    }

    public <T, U> void accept(BiConsumer<T, U> task, T left, U right) {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            task.accept(left, right);
        }
    }

    public <T, U, R> R apply(BiFunction<T, U, R> task, T left, U right) {
        Objects.requireNonNull(task, "task must not be null");
        try (PrivacyTenantContextScope ignored = openScope()) {
            return task.apply(left, right);
        }
    }
}
