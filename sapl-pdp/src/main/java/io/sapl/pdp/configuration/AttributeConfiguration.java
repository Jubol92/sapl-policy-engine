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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClients;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.attributes.*;
import io.sapl.attributes.libraries.UserPolicyInformationPoint;
import io.sapl.attributes.storage.HeapAttributeStorage;
import io.sapl.attributes.storage.MongoAttributeStorage;
import io.sapl.attributes.storage.PostgresAttributeStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import java.time.Clock;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

// Used to create one instance of AttributeStorage, AttributeRepository and the AttributeBroker
@Configuration
public class AttributeConfiguration {

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "heap")
    public AttributeStorage heapStorage() {
        return new HeapAttributeStorage();
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public AttributeStorage mongoStorage(ReactiveMongoTemplate template) {
        return new MongoAttributeStorage(template);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public AttributeStorage postgresStorage(DatabaseClient client, ObjectMapper mapper) {
        return new PostgresAttributeStorage(client, mapper);
    }

    @Bean
    public AttributeRepository attributeRepository(AttributeStorage storage) {
        return new InMemoryAttributeRepository(Clock.systemUTC(), storage);
    }

    @Bean
    public AttributeBroker attributeBroker(AttributeRepository repository) {
        var broker = new CachingAttributeBroker(repository);

        broker.loadPolicyInformationPointLibrary(new UserPolicyInformationPoint());

        return broker;
    }

    @Bean
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER, "postgresql")
                .option(HOST, "localhost").option(PORT, 5432).option(USER, "sapl").option(PASSWORD, "secret")
                .option(DATABASE, "sapl").build());
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);

        return mapper;
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public ReactiveMongoTemplate reactiveMongoTemplate(@Value("${spring.data.mongodb.uri}") String uri) {
        ConnectionString cs = new ConnectionString(uri);
        return new ReactiveMongoTemplate(MongoClients.create(cs), cs.getDatabase());
    }
}
