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
package io.sapl.attributeapi.attributes.backend;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.sapl.api.model.Value;
import java.time.Duration;
import java.util.List;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.*;

@SuppressWarnings("unused")
public class RedisAttributeStore implements AttributeStore {
    private static final String ERROR_TTL_NOT_POSITIVE = "Ttl must be a strictly positive Duration.";
    private static final String UNDEFINED_STRING       = "UNDEFINED";

    private final RedisClient                             client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String>           cli;

    // todo: Replace/remove as soon it's clarified how to add the pdpId to the
    // Repository
    private final String pdpId = "default";

    public RedisAttributeStore(RedisClient client) {
        this.client     = client;
        this.connection = client.connect();
        this.cli        = connection.sync();
    }

    @Override
    public void publish(AttributeSignature signature, Value value) {
        publishInternal(signature, value, null);
    }

    @Override
    public void publish(AttributeSignature signature, Value value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        publishInternal(signature, value, ttl);
    }

    private void publishInternal(AttributeSignature signature, @NonNull Value value, @Nullable Duration ttl) {
        String redisKey   = toRedisKey(signature);
        String redisValue = ValueJsonMarshaller.toJsonString(value);

        if (ttl == null) {
            cli.set(redisKey, redisValue);
        } else {
            cli.setex(redisKey, ttl.toSeconds(), redisValue);
        }
        cli.publish("sapl:changes:" + redisKey, redisValue);
    }

    @Override
    public void remove(AttributeSignature signature) {
        String redisKey = toRedisKey(signature);
        cli.del(redisKey);
        cli.publish("sapl:changes:" + redisKey, UNDEFINED_STRING);
    }

    @Override
    public Value get(AttributeSignature signature) {
        var raw = cli.get(toRedisKey(signature));

        return raw != null ? ValueJsonMarshaller.json(raw) : Value.UNDEFINED;
    }

    @Override
    public void close() {
        connection.close();
        client.close();
    }

    private String toRedisKey(AttributeSignature signature) {
        String entity    = signature.entity() != null ? ValueJsonMarshaller.toJsonString(signature.entity()) : "null";
        String arguments = valuesToJson(signature.arguments());

        return "sapl:attribute:" + pdpId + ":" + entity + ":" + signature.name() + ":" + arguments;
    }

    private String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }
}
