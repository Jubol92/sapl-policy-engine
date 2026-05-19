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
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.SneakyThrows;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

public class MongoAttributeStorage implements AttributeStorage {
    private final ReactiveMongoTemplate mongo;
    private final ObjectMapper          mapper;

    public MongoAttributeStorage(ReactiveMongoTemplate mongo, ObjectMapper mapper) {
        this.mongo  = mongo;
        this.mapper = mapper;
    }

    @Override
    @SneakyThrows
    public void put(RepositoryKey key, StorageEntry entry) {
        var document = new Document();
        document.put("_id", getId(key));
        document.put("value", mapper.writeValueAsString(entry.value()));
        document.put("expiresAt", entry.expiresAt() != null ? entry.expiresAt().toString() : null);
        mongo.save(document, "attributes").block();
    }

    @Override
    public void remove(RepositoryKey key) {
        mongo.remove(new Query(Criteria.where("_id").is(getId(key))), "attributes").block();
    }

    @Override
    public Map<RepositoryKey, StorageEntry> findAll() {
        return mongo.find(new Query(), Document.class, "attributes")
                .collectMap(doc -> parseId(doc.getString("_id")),
                        doc -> new StorageEntry(deserializeValue(doc.getString("value")),
                                doc.getString("expiresAt") != null ? Instant.parse(doc.getString("expiresAt")) : null))
                .block();
    }

    @Override
    public void close() {
        // ReactiveMongoTemplate lifecycle is managed by Spring context
    }

    private String getId(RepositoryKey key) {
        return (key.entity() == null ? "_" : key.entity().toString()) + ":" + key.name();
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
}
