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

import org.jspecify.annotations.Nullable;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.repository.RepositoryKey;

import java.time.Instant;
import java.util.Map;

public interface AttributeStorage extends AutoCloseable {
    record StorageEntry(Value value, @Nullable Instant expiresAt) {
        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    void put(RepositoryKey key, StorageEntry entry);

    void remove(RepositoryKey key);

    Map<RepositoryKey, StorageEntry> findAll();

    @Override
    void close();
}
