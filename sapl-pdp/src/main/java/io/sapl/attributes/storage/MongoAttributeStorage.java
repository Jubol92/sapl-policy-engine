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
import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import lombok.SneakyThrows;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    public Mono<PersistedAttribute> get(AttributeKey key) {
        return mongo.find(Query.query(Criteria.where("_id").is(getId(key))), Document.class, "attributes").next()
                .mapNotNull(doc -> deserialize(doc.getString("data")));
    }

    @Override
    @SneakyThrows
    public Mono<Void> put(AttributeKey key, PersistedAttribute value) {
        var document = new Document();
        document.put("_id", getId(key));
        document.put("data", mapper.writeValueAsString(value));
        return mongo.save(document, "attributes").then();
    }

    // Removes the key from the storage
    @Override
    public Mono<Void> remove(AttributeKey key) {
        Query query = new Query(Criteria.where("_id").is(getId(key)));
        return mongo.remove(query, "attributes").then();
    }

    @Override
    public Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAll() {
        return mongo.find(new Query(), Document.class, "attributes").mapNotNull(doc -> {
            var persisted = deserialize(doc.getString("data"));
            if (persisted == null)
                return null;
            return Map.entry(parseId(doc.getString("_id")), persisted);
        });
    }

    // Parses "_id" back to AttributeKey. Format: entity.toString():attributeName
    // Entity is "_" for null or a JSON string e.g. "alice".
    private AttributeKey parseId(String id) {
        if (id == null)
            return null;
        if (id.startsWith("_:")) {
            return new AttributeKey(null, id.substring(2), new ArrayList<>());
        }
        int colonIdx;
        if (id.charAt(0) == '"') {
            int close = 1;
            while (close < id.length() && (id.charAt(close) != '"' || id.charAt(close - 1) == '\\')) {
                close++;
            }
            colonIdx = close + 1;
        } else {
            colonIdx = id.indexOf(':');
        }
        var entityJson    = id.substring(0, colonIdx);
        var attributeName = id.substring(colonIdx + 1);
        var entity        = Value.of(entityJson.replaceAll("^\"|\"$", ""));
        return new AttributeKey(entity, attributeName, new ArrayList<>());
    }

    private String getId(AttributeKey key) {
        return (key.entity() == null ? "_" : key.entity().toString()) + ":" + key.attributeName();
    }

    @SneakyThrows
    private PersistedAttribute deserialize(String json) {
        if (json == null)
            return null;
        return mapper.readValue(json, PersistedAttribute.class);
    }
}
