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
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import java.time.Duration;
import java.util.function.Consumer;

public class MongoAttributeRepository implements AttributeRepository {
    private final ReactiveMongoTemplate mongo;
    private final ObjectMapper          mapper;

    public MongoAttributeRepository(ReactiveMongoTemplate mongo, ObjectMapper mapper) {
        this.mongo  = mongo;
        this.mapper = mapper;
    }

    @Override
    public void close() {

    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {

    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {

    }

    @Override
    public void remove(@NonNull RepositoryKey key) {

    }

    @Override
    public Registration observe(@NonNull AttributeFinderInvocation invocation, @NonNull Consumer<Value> onValue) {
        return null;
    }
}
