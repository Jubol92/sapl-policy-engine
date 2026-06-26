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

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import org.springframework.r2dbc.core.DatabaseClient;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class PostgresAttributeStore implements AttributeStore {
    private static final String ERROR_TTL_NOT_POSITIVE = "Ttl must be a strictly positive Duration.";

    private final DatabaseClient client;

    // todo: Replace/remove as soon it's clarified how to add the pdpId to the
    // Repository
    private final String pdpId = "default";

    public PostgresAttributeStore(DatabaseClient client) {
        this.client = client;
    }

    @Override
    public void publish(AttributeSignature key, Value value) {
        upsertToDB(key, value, null);
    }

    @Override
    public void publish(AttributeSignature key, Value value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(ERROR_TTL_NOT_POSITIVE);
        }
        upsertToDB(key, value, Instant.now().plus(ttl));
    }

    @Override
    public void remove(AttributeSignature key) {
        deleteFromDB(key);
    }

    @Override
    public Value get(AttributeSignature key) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());

        var spec = client.sql("SELECT value FROM attributes WHERE pdp_id = :pdpId AND name = :name "
                + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                + "AND arguments = CAST(:arguments AS jsonb) " + "AND (expires_at IS NULL OR expires_at > NOW())")
                .bind("pdpId", pdpId).bind("name", key.name()).bind("arguments", argumentsJson);

        return (entityJson != null ? spec.bind("entity", entityJson) : spec.bindNull("entity", String.class)).map(r -> {
            String raw = r.get("value", String.class);
            return raw != null ? ValueJsonMarshaller.json(raw) : Value.UNDEFINED;
        }).one().blockOptional().orElse(Value.UNDEFINED);
    }

    @Override
    public void close() {
    }

    private void upsertToDB(@NonNull AttributeSignature key, Value value, @Nullable Instant expiresAt) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());
        var valueJson     = ValueJsonMarshaller.toJsonString(value);

        // ON CONFLICT triggers the unique constraint in the db if the value already
        // exists
        // Indexes:
        // "attributes_pdp_id_name_entity_arguments_key" UNIQUE CONSTRAINT, btree
        // (pdp_id, name, entity, arguments) NULLS NOT DISTINCT
        // DO UPDATE executes an update statement instead. This logic implements a real
        // upsert and an atomic execution
        // The atomic execution is important to have the same Decision if a multi node
        // setup is used
        var upsertSpec = client.sql("INSERT INTO attributes (pdp_id, name, entity, arguments, value, expires_at) "
                + "VALUES (:pdpId, :name, CAST(:entity AS jsonb), CAST(:arguments AS jsonb), CAST(:value AS jsonb), :expiresAt) "
                + "ON CONFLICT (pdp_id, name, entity, arguments) "
                + "DO UPDATE SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at").bind("pdpId", pdpId)
                .bind("name", key.name()).bind("arguments", argumentsJson).bind("value", valueJson);

        upsertSpec = expiresAt != null ? upsertSpec.bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                : upsertSpec.bindNull("expiresAt", OffsetDateTime.class);

        (entityJson != null ? upsertSpec.bind("entity", entityJson) : upsertSpec.bindNull("entity", String.class))
                .then().block();
    }

    private void deleteFromDB(@NonNull AttributeSignature key) {
        var entityJson    = key.entity() != null ? ValueJsonMarshaller.toJsonString(key.entity()) : null;
        var argumentsJson = valuesToJson(key.arguments());

        var spec = client
                .sql("DELETE FROM attributes " + "WHERE pdp_id = :pdpId AND name = :name "
                        + "AND entity IS NOT DISTINCT FROM CAST(:entity AS jsonb) "
                        + "AND arguments = CAST(:arguments AS jsonb)")
                .bind("pdpId", pdpId).bind("name", key.name()).bind("arguments", argumentsJson);
        (entityJson != null ? spec.bind("entity", entityJson) : spec.bindNull("entity", String.class)).then().block();
    }

    private static String valuesToJson(List<Value> values) {
        return ValueJsonMarshaller.toJsonString(Value.ofArray(values));
    }

}
