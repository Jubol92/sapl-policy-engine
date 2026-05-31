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
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import lombok.NonNull;
import lombok.experimental.Delegate;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@SuppressWarnings("unused")
public class MongoAttributeRepository implements AttributeRepository, ReadableAttributeRepository {
    // Delegate Pattern . observer(), close() etc are generated
    @Delegate(excludes = ExcludedMethods.class)
    private final InMemoryAttributeRepository internalRepository;

    @Override
    public Value get(RepositoryKey key) {
        return null;
    }

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

    public void loadFromDB() {
        mongo.find(new Query(), Document.class, "attributes").toStream().forEach(doc -> {
            var key       = keyFromDoc(doc);
            var value     = fromMongoDocument(doc.get("value", Document.class));
            var dateField = doc.getDate("expiresAt");
            var expiresAt = dateField != null ? dateField.toInstant() : null;

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
        });
    }

    private RepositoryKey keyFromDoc(Document doc) {
        var entityDoc = doc.get("entity", Document.class);
        var argDocs   = doc.getList("arguments", Document.class);
        return new RepositoryKey(entityDoc != null ? fromMongoDocument(entityDoc) : null, doc.getString("name"),
                argDocs != null ? argDocs.stream().map(this::fromMongoDocument).toList() : List.of());
    }

    public void deleteFromDB(@NonNull RepositoryKey key) {
        mongo.remove(doMongoQuery(key), "attributes").block();
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

    private void upsertToDB(@NonNull RepositoryKey key, Value value, @Nullable Instant expiresAt) {
        var update = new Update().set("name", key.name())
                .set("entity", key.entity() != null ? toMongoDocument(key.entity()) : null)
                .set("arguments", key.arguments().stream().map(this::toMongoDocument).toList())
                .set("value", toMongoDocument(value)).set("expiresAt", expiresAt != null ? Date.from(expiresAt) : null);

        mongo.upsert(doMongoQuery(key), update, "attributes").block();
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        internalRepository.remove(key);
        deleteFromDB(key);
    }

    @SneakyThrows
    private Document toMongoDocument(Value value) {
        return Document.parse(mapper.writeValueAsString(value));
    }

    @SneakyThrows
    private Value fromMongoDocument(Document doc) {
        return mapper.readValue(doc.toJson(), Value.class);
    }

    private Query doMongoQuery(RepositoryKey key) {
        var criteria = Criteria.where("name").is(key.name()).and("entity")
                .is(key.entity() != null ? toMongoDocument(key.entity()) : null).and("arguments")
                .is(key.arguments().stream().map(this::toMongoDocument).toList());
        return new Query(criteria);
    }
}
