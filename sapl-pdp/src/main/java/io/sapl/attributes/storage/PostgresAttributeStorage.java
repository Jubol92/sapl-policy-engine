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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.attributes.broker.repository.RepositoryKey;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.ArrayList;
import java.util.Map;

public class PostgresAttributeStorage implements AttributeStorage {
    private final DatabaseClient client;
    private final ObjectMapper   mapper;

    public PostgresAttributeStorage(DatabaseClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public void put(RepositoryKey key, StorageEntry entry) {
        client.sql("""
                INSERT INTO attributes(key, value)
                VALUES (:key, CAST(:value AS jsonb))
                ON CONFLICT (key) DO UPDATE SET value = CAST(:value AS jsonb)
                """).bind("key", serializeKey(key)).bind("value", serialize(entry)).then().block();
    }

    @Override
    public void remove(RepositoryKey key) {
        client.sql("DELETE FROM attributes WHERE key = :key").bind("key", serializeKey(key)).then().block();
    }

    @Override
    public Map<RepositoryKey, StorageEntry> findAll() {
        return client.sql("SELECT key, value FROM attributes")
                .map(row -> Map.entry(deserializeKey(row.get("key", String.class)),
                        deserialize(row.get("value", String.class))))
                .all().collectMap(Map.Entry::getKey, Map.Entry::getValue).block();
    }

    @Override
    public void close() {
        // DatabaseClient lifecycle managed by Spring context
    }

    private String serialize(StorageEntry entry) {
        try {
            return mapper.writeValueAsString(entry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StorageEntry deserialize(String json) {
        try {
            return mapper.readValue(json, StorageEntry.class);
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
}
