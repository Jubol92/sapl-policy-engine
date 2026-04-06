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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
public class PersistentAttributeStorage implements AttributeStorage {

    private final Path         Datastore = Paths.get("datastore");
    private final ObjectMapper mapper    = new ObjectMapper();

    public PersistentAttributeStorage() {
        try {
            if (!Files.exists(Datastore)) {
                Files.createDirectories(Datastore);
            }

            // Detect if the storage is empty or not. Storages that are not empty should be
            // logged
            try (Stream<Path> files = Files.list(Datastore)) {
                if (files.findAny().isPresent()) {
                    log.info("Storage is not empty. Loading data from storage");
                }
            }

        } catch (IOException ex) {
            throw new RuntimeException("Storage could not be created.", ex);
        }
    }

    @Override
    public Mono<PersistedAttribute> get(AttributeKey key) {
        return Mono.fromCallable(() -> {
            Path file = resolve(key);

            if (!Files.exists(file)) {
                return null;
            }
            return mapper.readValue(file.toFile(), PersistedAttribute.class);
        });
    }

    @Override
    public Mono<Void> put(AttributeKey key, PersistedAttribute value) {
        return Mono.fromRunnable(() -> {
            Path file = resolve(key);

            // If the parent folder doesn't exist: create it
            try {
                // Only create the folder if directory doesn't exist. No if needed
                Files.createDirectories(file.getParent());

                mapper.writeValue(file.toFile(), value);
                log.info("Successfully wrote {} into data store", value.toString());
            } catch (IOException ex) {
                throw new RuntimeException("Could not create directory.", ex);
            }
        });
    }

    @Override
    public Mono<Void> remove(AttributeKey key) {
        return Mono.fromRunnable(() -> {
            Path file = resolve(key);

            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new RuntimeException("Could not delete attribute from disk", e);
            }
        });
    }

    @Override
    public Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAll() {
        return Flux.create(sink -> {
            try (Stream<Path> paths = Files.walk(Datastore)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        PersistedAttribute value = mapper.readValue(file.toFile(), PersistedAttribute.class);

                        AttributeKey key = parseKey(file);

                        sink.next(Map.entry(key, value));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });

                sink.complete();
            } catch (IOException e) {
                sink.error(e);
            }
        });
    }

    // Internal resolver to find the right attribute
    private Path resolve(AttributeKey key) {
        // Crucial attributes for the path
        String entity    = key.entity().toString().replace("\"", "");
        String attribute = key.attributeName();

        // Return the right path and file name with the .json extension
        return Datastore.resolve(entity).resolve(attribute + ".json");
    }

    private AttributeKey parseKey(Path file) {
        String entity = file.getParent().getFileName().toString();

        String attribute = file.getFileName().toString().replace(".json", "");

        return new AttributeKey(Value.of(entity), attribute, List.of());
    }
}
