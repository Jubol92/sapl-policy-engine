/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.attributes.broker.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
public class PostgresAttributeRepository implements AttributeRepository {
    private static final String ERROR_CLOSED           = "Repository is closed.";
    private static final String ERROR_TTL_NOT_POSITIVE = "Ttl must be a strictly positive Duration.";
    private static final String WARN_OBSERVER_THREW    = "Observer {} threw: {}.";

    private final ScheduledExecutorService scheduler;

    private final DatabaseClient client;
    private final ObjectMapper   mapper;

    private final ReentrantLock                                                    lock           = new ReentrantLock(
            true);
    private final Map<RepositoryKey, PostgresAttributeRepository.Entry>            entries        = new HashMap<>();
    private final Map<RepositoryKey, Set<PostgresAttributeRepository.KeyObserver>> observersByKey = new HashMap<>();

    private boolean closed = false;

    @Override
    public void close() {
        Collection<PostgresAttributeRepository.Entry> toCancel;
        lock.lock();

        try {
            if (closed) {
                return;
            }
            closed   = true;
            toCancel = new ArrayList<>(entries.values());
            // Mark every observer closed so in-flight fires (already past the
            // observers gate, about to call deliver) become no-ops.
            for (val bucket : observersByKey.values()) {
                for (val observer : bucket) {
                    observer.closed = true;
                }
            }
            entries.clear();
            observersByKey.clear();
        } finally {

            lock.unlock();

        }
        for (val e : toCancel) {
            if (e.expiryTask != null) {
                e.expiryTask.cancel(false);
            }
        }
        scheduler.shutdownNow();
    }

    public PostgresAttributeRepository(DatabaseClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            val thread = Thread.ofVirtual().unstarted(runnable);
            thread.setName("PostgresAttributeRepository-ttl");
            return thread;
        });

        startUp();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {

        publishInternal(key, value, null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        publishInternal(key, value, ttl);
    }

    private void publishInternal(RepositoryKey key, Value value, @Nullable Duration ttl) {
        List<PostgresAttributeRepository.KeyObserver> toFire;
        lock.lock();

        try {
            if (closed) {
                return;
            }

            val prior = entries.get(key);

            if (prior != null && prior.expiryTask != null) {
                prior.expiryTask.cancel(false);
            }

            val entry = new PostgresAttributeRepository.Entry(value);
            entries.put(key, entry);

            // Persist to storage
            var expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
            persistEntry(key, value, expiresAt);

            if (ttl != null) {
                entry.expiryTask = scheduler.schedule(() -> expireKey(key, entry), ttl.toMillis(),
                        TimeUnit.MILLISECONDS);
            }
            toFire = observers(key);
        } finally {
            lock.unlock();
        }
        fireObservers(toFire, value);
    }

    private record dbrow(String key, String value, java.time.OffsetDateTime expiresAt) {}

    public void startUp() {
        cleanup();
        var rows = client.sql("SELECT key, value, expires_at FROM attributes")
                .map(row -> new dbrow(row.get("key", String.class), row.get("value", String.class),
                        row.get("expires_at", java.time.OffsetDateTime.class)))
                .all().collectList().blockOptional().orElse(List.of());

        for (var row : rows) {
            try {
                var key       = deserializeKey(row.key());
                var value     = mapper.readValue(row.value(), Value.class);
                var expiresAt = row.expiresAt() != null ? row.expiresAt().toInstant() : null;

                var entry = new Entry(value);
                entries.put(key, entry);

                if (expiresAt != null) {
                    var restTtl = Duration.between(Instant.now(), expiresAt);
                    entry.expiryTask = scheduler.schedule(() -> expireKey(key, entry), restTtl.toMillis(),
                            TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Simple clean-up to delete all expired keys. Important for the start-up of the
    // node
    public void cleanup() {
        client.sql("DELETE FROM attributes WHERE expires_at < NOW()").then().block();
    }

    private void expireKey(RepositoryKey key, PostgresAttributeRepository.Entry expectedEntry) {
        List<PostgresAttributeRepository.KeyObserver> toFire;
        lock.lock();

        try {
            val current = entries.get(key);
            if (current != expectedEntry) {
                return;
            }
            entries.remove(key);
            toFire = observers(key);
        } finally {
            lock.unlock();
        }

        // DELETE the key from the postgres backend as well.
        client.sql("DELETE FROM attributes WHERE key = :key").bind("key", serializeKey(key)).then().block();
        fireObservers(toFire, Value.UNDEFINED);
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        List<PostgresAttributeRepository.KeyObserver> toFire;
        lock.lock();

        try {
            if (closed) {
                return;
            }
            val prior = entries.remove(key);
            client.sql("DELETE FROM attributes WHERE key = :key").bind("key", serializeKey(key)).then().block();
            if (prior == null) {
                return;
            }
            if (prior.expiryTask != null) {
                prior.expiryTask.cancel(false);
            }
            toFire = observers(key);
        } finally {

            lock.unlock();

        }
        fireObservers(toFire, Value.UNDEFINED);
    }

    @Override
    public Registration observe(@NonNull AttributeFinderInvocation invocation, @NonNull Consumer<Value> onValue) {
        val   repoKey  = RepositoryKey.fromInvocation(invocation);
        val   observer = new PostgresAttributeRepository.KeyObserver(repoKey, onValue);
        Value initial;
        lock.lock();

        try {
            if (closed) {
                initial = Value.error(ERROR_CLOSED);
            } else {
                observersByKey.computeIfAbsent(repoKey, k -> new HashSet<>()).add(observer);
                val entry = entries.get(repoKey);
                initial = entry != null ? entry.value : Value.UNDEFINED;
            }
        } finally {

            lock.unlock();

        }
        observer.deliver(initial);
        return observer;
    }

    /** Caller holds the lock. */
    private List<PostgresAttributeRepository.KeyObserver> observers(RepositoryKey repositoryKey) {
        val bucket = observersByKey.get(repositoryKey);
        return bucket == null ? List.of() : new ArrayList<>(bucket);
    }

    private void fireObservers(List<PostgresAttributeRepository.KeyObserver> observers, Value value) {
        for (val observer : observers) {
            observer.deliver(value);
        }
    }

    // Writes back the attribute into the Postgres backend
    private void persistEntry(RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        try {
            var serializedValue = mapper.writeValueAsString(value);

            // Upsert (update or insert)
            var spec = client.sql("""
                    INSERT INTO attributes(key, value, expires_at)
                    VALUES (:key, CAST(:value AS jsonb), :expires_at)
                    ON CONFLICT (key) DO UPDATE SET value = CAST(:value AS jsonb), expires_at = :expires_at
                    """).bind("key", serializeKey(key)).bind("value", serializedValue);

            // expires_key is allowed to be null
            if (expiresAt != null)
                spec = spec.bind("expires_at", expiresAt.atOffset(java.time.ZoneOffset.UTC));
            else
                spec = spec.bindNull("expires_at", java.time.OffsetDateTime.class);
            spec.then().block();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeKey(RepositoryKey key) {
        try {
            var normalized = new RepositoryKey(key.entity(), key.name(), new ArrayList<>(key.arguments()));
            return mapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RepositoryKey deserializeKey(String json) {
        try {
            return mapper.readValue(json, RepositoryKey.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Per-entry storage. {@code expiryTask} is settable so the
     * publisher can install it after constructing the entry record
     * that the task closure captures.
     */
    private static final class Entry {
        private final Value        value;
        @Nullable
        private ScheduledFuture<?> expiryTask;

        private Entry(Value value) {
            this.value = value;
        }
    }

    /**
     * Single-key observer registered via {@link #observe}. The
     * repository indexes observers in {@link #observersByKey} and
     * fires them on publish, expire and remove.
     */
    @RequiredArgsConstructor
    private final class KeyObserver implements AttributeRepository.Registration {

        private static final AtomicLong NEXT_ID = new AtomicLong(Long.MIN_VALUE);

        private final long            id     = NEXT_ID.getAndIncrement();
        private final RepositoryKey   repositoryKey;
        private final Consumer<Value> onValue;
        private volatile boolean      closed = false;

        void deliver(Value value) {
            if (closed) {
                return;
            }
            try {
                onValue.accept(value);
            } catch (RuntimeException e) {
                log.warn(WARN_OBSERVER_THREW, id, e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            lock.lock();

            try {
                if (closed) {
                    return;
                }
                closed = true;
                val bucket = observersByKey.get(repositoryKey);
                if (bucket != null) {
                    bucket.remove(this);
                    if (bucket.isEmpty()) {
                        observersByKey.remove(repositoryKey);
                    }
                }
            } finally {

                lock.unlock();

            }
        }
    }
}
