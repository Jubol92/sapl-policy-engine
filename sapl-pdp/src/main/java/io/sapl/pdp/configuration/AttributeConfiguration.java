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
package io.sapl.pdp.configuration;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.HeapAttributeStorage;
import io.sapl.attributes.InMemoryAttributeRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Used to create one instance of AttributeStorage, AttributeRepository and the AttributeBroker
@Configuration
public class AttributeConfiguration {

    @Bean
    public AttributeStorage attributeStorage() {
        return new HeapAttributeStorage();
    }

    @Bean
    public AttributeRepository attributeRepository(AttributeStorage storage) {
        return new InMemoryAttributeRepository(Clock.systemUTC(), storage);
    }

    @Bean
    public AttributeBroker attributeBroker(AttributeRepository repository) {
        return new CachingAttributeBroker(repository);
    }
}
