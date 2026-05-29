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

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RedisAttributeRepository implements AttributeRepository {
    private static final String ERROR_TTL_NOT_POSITIVE = "Ttl must be a strictly positive Duration.";
    private static final String ERROR_CLOSED           = "Repository is closed.";

    private final RedisClient                                   client;
    private final StatefulRedisConnection<String, String>       connection;
    private final RedisCommands<String, String>                 cli;
    private final StatefulRedisPubSubConnection<String, String> pubsub;

    private final Map<String, Set<Consumer<Value>>> observersByKey = new ConcurrentHashMap<>();

    private boolean closed = false;

    public RedisAttributeRepository(RedisClient client) {
        this.client     = client;
        this.connection = client.connect();
        this.pubsub     = client.connectPubSub();
        this.cli        = connection.sync();

        // Subscribe to needed channels
        pubsub.sync().psubscribe("sapl:changes:*");
        pubsub.sync().subscribe("__keyevent@0__:expired");

        pubsub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                // expired-Events: message = der abgelaufene Redis-Key
                notifyObservers(message, Value.UNDEFINED);
            }

            @Override
            public void message(String pattern, String channel, String message) {
                // sapl:changes:* → Wertänderungen
                String redisKey = channel.substring("sapl:changes:".length());
                notifyObservers(redisKey, toValueFromRedisValue(message));
            }
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pubsub.sync().unsubscribe();
        pubsub.sync().punsubscribe();
        connection.close();
        client.close();
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

    private void publishInternal(@NonNull RepositoryKey key, @NonNull Value value, @Nullable Duration ttl) {
        String redisKey   = toRedisKey(key);
        String redisValue = toRawString(value);
        if (ttl == null) {
            cli.set(redisKey, redisValue);
        } else {
            cli.setex(redisKey, ttl.toSeconds(), redisValue);
        }
        cli.publish("sapl:changes:" + redisKey, redisValue);
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        cli.del(toRedisKey(key));
        cli.publish("sapl:changes:" + toRedisKey(key), "UNDEFINED");
    }

    public Value get(@NonNull RepositoryKey key) {
        return toValueFromRedisValue(cli.get(toRedisKey(key)));
    }

    @Override
    public Registration observe(@NonNull AttributeFinderInvocation invocation, @NonNull Consumer<Value> onValue) {
        if (closed) {
            Value.error(ERROR_CLOSED);
        }

        RepositoryKey key      = new RepositoryKey(invocation.entity(), invocation.attributeName(),
                invocation.arguments());
        String        redisKey = toRedisKey(key);

        // Register callback for future changes
        observersByKey.computeIfAbsent(redisKey, k -> ConcurrentHashMap.newKeySet()).add(onValue);

        // Deliver current value immediately
        onValue.accept(get(key));

        // Return handle to unregister the callback
        return () -> observersByKey.getOrDefault(redisKey, Set.of()).remove(onValue);
    }

    private String toRedisKey(RepositoryKey key) {
        String entity    = key.entity() != null ? toRawString(key.entity()) : "";
        String arguments = key.arguments().stream().map(this::toRawString).toList().toString();

        return "sapl:attribute:" + entity + ":" + key.name() + ":" + arguments;
    }

    private String toRawString(Value value) {
        return switch (value) {
        case TextValue(String s) -> s;
        default                  -> value.toString();
        };
    }

    private Value toValueFromRedisValue(String value) {
        if (value == null)
            return Value.UNDEFINED;
        if (value.equalsIgnoreCase("true"))
            return Value.of(true);
        if (value.equalsIgnoreCase("false"))
            return Value.of(false);
        try {
            return Value.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
        }
        try {
            return Value.of(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
        }
        return Value.of(value);
    }

    private void notifyObservers(String redisKey, Value value) {
        var observers = observersByKey.getOrDefault(redisKey, Set.of());
        observers.forEach(callback -> callback.accept(value));
    }
}
