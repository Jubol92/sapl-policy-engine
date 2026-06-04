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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

// Using R2DBC because it's reactive. JPA/Hibernate would block
@SuppressWarnings("unused")
public class PostgresAttributeRepository implements AttributeRepository, ReadableAttributeRepository {

    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = PostgresAttributeRepository.ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    private final DatabaseClient client;

    public PostgresAttributeRepository(DatabaseClient client) {
        this.client             = client;
        this.internalRepository = new InMemoryAttributeRepository(this::deleteFromDB);
        loadFromDB();
    }

    @Override
    public Value get(RepositoryKey key) {
        return internalRepository.get(key);
    }

    private interface ExcludedMethods {
        void publish(RepositoryKey key, Value value);

        void publish(RepositoryKey key, Value value, Duration ttl);

        void remove(RepositoryKey key);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        internalRepository.publish(key, value);
        upsertToDB(key, value, null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        internalRepository.publish(key, value, ttl);
        upsertToDB(key, value, Instant.now().plus(ttl));
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        internalRepository.remove(key);
        deleteFromDB(key);
    }

    private record DBEntry(String name, String entity, String arguments, String value, OffsetDateTime expiresAt) {}

    public void loadFromDB() {
        var rows = client.sql("SELECT name, entity, arguments, value, expires_at FROM attributes")
                .map(row -> new DBEntry(row.get("name", String.class), row.get("entity", String.class),
                        row.get("arguments", String.class), row.get("value", String.class),
                        row.get("expires_at", OffsetDateTime.class)))
                .all().collectList().block();

        if (rows == null)
            return;

        for (var row : rows) {
            var key       = new RepositoryKey(row.entity() != null ? ValueJsonMarshaller.json(row.entity()) : null,
                    row.name(), jsonToValues(row.arguments()));
            var value     = ValueJsonMarshaller.json(row.value());
            var expiresAt = row.expiresAt() != null ? row.expiresAt().toInstant() : null;

            if (expiresAt != null) {
                var remainingTTL = Duration.between(Instant.now(), expiresAt);
                if (!remainingTTL.isNegative()) {
                    internalRepository.publish(key, value, remainingTTL);
                } else {
                    deleteFromDB(key);
                }
            } else {
                internalRepository.publish(key, value);
            }
        }
    }

    private void upsertToDB(@NonNull RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());
        var valueJson     = ValueJsonMarshaller.toJsonString(value);

        var deleteSpec = client
                .sql("DELETE FROM attributes " + "WHERE name = :name "
                        + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                        + "AND arguments = CAST(:arguments AS jsonb)")
                .bind("name", key.name()).bind("arguments", argumentsJson);
        (entityJson != null ? deleteSpec.bind("entity", entityJson) : deleteSpec.bindNull("entity", String.class))
                .then().block();

        var insertSpec = client.sql("INSERT INTO attributes (name, entity, arguments, value, expires_at) "
                + "VALUES (:name, CAST(:entity AS jsonb), CAST(:arguments AS jsonb), CAST(:value AS jsonb), :expiresAt)")
                .bind("name", key.name()).bind("arguments", argumentsJson).bind("value", valueJson);

        insertSpec = expiresAt != null ? insertSpec.bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                : insertSpec.bindNull("expiresAt", OffsetDateTime.class);

        (entityJson != null ? insertSpec.bind("entity", entityJson) : insertSpec.bindNull("entity", String.class))
                .then().block();
    }

    public void deleteFromDB(@NonNull RepositoryKey key) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());

        var spec = client
                .sql("DELETE FROM attributes " + "WHERE name = :name "
                        + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                        + "AND arguments = CAST(:arguments AS jsonb)")
                .bind("name", key.name()).bind("arguments", argumentsJson);
        (entityJson != null ? spec.bind("entity", entityJson) : spec.bindNull("entity", String.class)).then().block();
    }

    private static String valuesToJson(List<Value> values) {
        if (values.isEmpty())
            return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append(ValueJsonMarshaller.toJsonString(values.get(i)));
        }
        return sb.append(']').toString();
    }

    private static List<Value> jsonToValues(String json) {
        if (json == null || json.isBlank())
            return List.of();
        var parsed = ValueJsonMarshaller.json(json);
        return parsed instanceof ArrayValue arr ? arr.stream().toList() : List.of();
    }
}
