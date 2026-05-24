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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import org.jspecify.annotations.Nullable;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.bson.Document;  // still needed for loadFromDB
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

@SuppressWarnings("unused")
public class MongoAttributeRepository implements AttributeRepository {
    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    private interface ExcludedMethods {
        void publish(RepositoryKey key, Value value);

        void publish(RepositoryKey key, Value value, Duration ttl);

        void remove(RepositoryKey key);
    }

    private final ReactiveMongoTemplate mongo;
    private final ObjectMapper          mapper;

    public MongoAttributeRepository(ReactiveMongoTemplate mongo, ObjectMapper mapper) {
        this.mongo              = mongo;
        this.mapper             = mapper;
        this.internalRepository = new InMemoryAttributeRepository(this::deleteFromDB);
        loadFromDB();
    }

    private record LoadedEntry(Value value, @Nullable Instant expiresAt) {}

    public void loadFromDB() {
        var entries = mongo.find(new Query(), Document.class, "attributes")
                .collectMap(doc -> parseId(doc.getString("_id")),
                        doc -> new LoadedEntry(deserializeValue(doc.getString("value")),
                                doc.getString("expiresAt") != null ? Instant.parse(doc.getString("expiresAt")) : null))
                .blockOptional().orElse(Map.of());

        for (var entry : entries.entrySet()) {
            var key       = entry.getKey();
            var value     = entry.getValue().value();
            var expiresAt = entry.getValue().expiresAt();

            // ignore expired keys
            if (expiresAt != null) {
                var remainingTTL = Duration.between(Instant.now(), expiresAt);
                if (!remainingTTL.isNegative()) {
                    internalRepository.publish(key, value, remainingTTL);
                } else {
                    deleteFromDB(key);
                }
            } else {
                internalRepository.publish(key, value); // no TTL
            }
        }
    }

    public void deleteFromDB(@NonNull RepositoryKey key) {
        mongo.remove(new Query(Criteria.where("_id").is(getId(key))), "attributes").block();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        internalRepository.publish(key, value);
        upsertToDB(key, serializeValue(value), null);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        internalRepository.publish(key, value, ttl);
        upsertToDB(key, serializeValue(value), Instant.now().plus(ttl).toString());
    }

    private void upsertToDB(@NonNull RepositoryKey key, String serializedValue, @Nullable String expiresAt) {
        var update = new Update().set("value", serializedValue).set("expiresAt", expiresAt);
        mongo.upsert(new Query(Criteria.where("_id").is(getId(key))), update, "attributes").block();
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        internalRepository.remove(key);
        deleteFromDB(key);
    }

    private RepositoryKey parseId(String id) {
        if (id == null)
            return null;

        if (id.startsWith("_:"))
            return new RepositoryKey(null, id.substring(2), new ArrayList<>());

        if (id.charAt(0) == '"') {
            int close = 1;
            while (close < id.length() && (id.charAt(close) != '"' || id.charAt(close - 1) == '\\')) {
                close++;
            }
            int colonPos      = close + 1;
            var entityJson    = id.substring(0, colonPos);
            var attributeName = id.substring(colonPos + 1);
            var entity        = Value.of(entityJson.replaceAll("^\"|\"$", ""));
            return new RepositoryKey(entity, attributeName, new ArrayList<>());
        }

        int colonPos      = id.indexOf(':');
        var entityJson    = id.substring(0, colonPos);
        var attributeName = id.substring(colonPos + 1);
        return new RepositoryKey(Value.of(entityJson), attributeName, new ArrayList<>());
    }

    @SneakyThrows
    private Value deserializeValue(String json) {
        if (json == null)
            return Value.NULL;
        return mapper.readValue(json, Value.class);
    }

    private String getId(RepositoryKey key) {
        return (key.entity() == null ? "_" : key.entity().toString()) + ":" + key.name();
    }

    private String serializeValue(Value value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
