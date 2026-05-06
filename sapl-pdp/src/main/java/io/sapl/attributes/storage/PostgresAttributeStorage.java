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
package io.sapl.attributes.storage;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.attributes.PersistedAttribute;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class PostgresAttributeStorage implements AttributeStorage {
    private final DatabaseClient client;
    private final ObjectMapper   mapper;

    public PostgresAttributeStorage(DatabaseClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Mono<PersistedAttribute> get(AttributeKey key) {
        // :key :value are replacy by the bind
        return client.sql("SELECT value FROM attributes WHERE key = :key").bind("key", serializeKey(key)).map(row -> {
            String json = row.get("value", String.class);
            return deserialize(json);
        }).one();
    }

    @Override
    public Mono<Void> put(AttributeKey key, PersistedAttribute value) {
        String json = serialize(value);

        // :key :value are replaced by the bind
        return client.sql("""
                INSERT INTO attributes(key, value)
                VALUES (:key, CAST(:value AS jsonb))
                ON CONFLICT (key) DO UPDATE SET value = CAST(:value AS jsonb)
                """).bind("key", serializeKey(key)).bind("value", json).then();
    }

    @Override
    public Mono<Void> remove(AttributeKey key) {
        // :key :value are replaced by the bind
        return client.sql("DELETE FROM attributes WHERE key = :key").bind("key", serializeKey(key)).then();
    }

    @Override
    public Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAll() {
        return client.sql("SELECT key, value FROM attributes").map(row -> {
            String keyStr = row.get("key", String.class);
            String json   = row.get("value", String.class);

            AttributeKey       key   = deserializeKey(keyStr);
            PersistedAttribute value = deserialize(json);

            return Map.entry(key, value);
        }).all();
    }

    // Serializes the value to put it into the db
    private String serialize(PersistedAttribute value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Deserialize the value to use it with the attribute repository / storage
    private PersistedAttribute deserialize(String json) {
        try {
            return mapper.readValue(json, PersistedAttribute.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeKey(AttributeKey key) {
        try {
            return mapper.writeValueAsString(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AttributeKey deserializeKey(String json) {
        try {
            return mapper.readValue(json, AttributeKey.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
