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

    // Gets all documents from the collection attributes within the MongoDB
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
        int colonPos;

        // No key - abort
        if (id == null) {
            return null;
        }

        // Global Attribute - key starts with _ when entity = null
        if (id.startsWith("_:")) {
            return new AttributeKey(null, id.substring(2), new ArrayList<>());
        }

        // remove the opening and closing quotation marks of the entity because a : can be also an attribute like user:role
        // the loop runs until the next quotation marks is found OR an escape sequence \\ ist detected
        // hint: 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16
        //       " a l i c e " : u s e  r  :  r  o  l  e
        //                   ^
        //                   |
        //                 stop here
        if (id.charAt(0) == '"') {
            int close = 1;
            while (close < id.length() && (id.charAt(close) != '"' || id.charAt(close - 1) == '\\')) {
                close++;
            }
            colonPos = close + 1;
        } else {
            // most common case: the entity is without a quotation marks or starts with _
            colonPos = id.indexOf(':');
        }

        var entityJson    = id.substring(0, colonPos); // string before first :
        var attributeName = id.substring(colonPos + 1); // string after the first :
        var entity        = Value.of(entityJson.replaceAll("^\"|\"$", "")); // remove all quotation marks

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
