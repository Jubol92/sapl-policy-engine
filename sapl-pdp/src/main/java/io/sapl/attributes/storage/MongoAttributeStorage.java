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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

public class MongoAttributeStorage implements AttributeStorage {
    private final ReactiveMongoTemplate mongo;

    public MongoAttributeStorage(ReactiveMongoTemplate mongo) {
        this.mongo = mongo;
    }

    // tbd
    @Override
    public Mono<PersistedAttribute> get(AttributeKey key) {
        return mongo.findById(getId(key), PersistedAttribute.class, "attributes");
    }

    // Puts the key into the storage. Needs to be a valid MongoDB document
    @Override
    public Mono<Void> put(AttributeKey key, PersistedAttribute value) {
        Map<String, Object> document = new HashMap<>();

        document.put("_id", getId(key));
        document.put("entity", key.entity());
        document.put("attribute", key.attributeName());
        document.put("value", value.value());
        document.put("timestamp", value.timestamp());
        document.put("ttl", value.ttl());
        document.put("timeoutStrategy", value.timeoutStrategy());
        document.put("timeoutDeadline", value.timeoutDeadline());

        return mongo.save(document, "attributes").then();
    }

    // Removes the key from the storage
    @Override
    public Mono<Void> remove(AttributeKey key) {
        Query query = new Query(Criteria.where("_id").is(getId(key)));
        return mongo.remove(query, "attributes").then();
    }

    // tbd
    @Override
    public Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAll() {
        return Flux.empty();
    }

    // Helper method to avoid inconsistent keys in the DB
    private String getId(AttributeKey key) {
        return (key.entity() == null ? "_" : key.entity().toString()) + ":" + key.attributeName();
    }
}
