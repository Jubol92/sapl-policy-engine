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
import io.lettuce.core.RedisClient;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.MongoAttributeRepository;
import io.sapl.attributes.broker.repository.PostgresAttributeRepository;
import io.sapl.attributes.broker.repository.ReadableAttributeRepository;
import io.sapl.attributes.broker.repository.RedisAttributeRepository;
import io.sapl.attributes.libraries.UserPolicyInformationPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@SuppressWarnings("unused")
@Configuration
public class AttributeConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "heap", matchIfMissing = true)
    public ReadableAttributeRepository heapAttributeRepository() {
        return new InMemoryAttributeRepository();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public ReadableAttributeRepository postgresAttributeRepository(DatabaseClient client,
            ConnectionFactory connection) {
        return new PostgresAttributeRepository(client, connection);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public ReadableAttributeRepository mongoAttributeRepository(ReactiveMongoTemplate template) {
        return new MongoAttributeRepository(template);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    public RedisClient redisClient(@Value("${spring.data.redis.url:redis://localhost:6379}") String uri) {
        return RedisClient.create(uri);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    public ReadableAttributeRepository redisAttributeRepository(RedisClient client) {
        return new RedisAttributeRepository(client);
    }

    @Bean
    public AttributeBroker attributeBroker(AttributeRepository repository) {
        return PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.systemUTC(),
                JsonMapper.builder().build(), true, List.of(new UserPolicyInformationPoint()), repository);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER, "postgresql")
                .option(HOST, "localhost").option(PORT, 5432).option(USER, "sapl").option(PASSWORD, "secret")
                .option(DATABASE, "sapl").build());
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public ReactiveMongoTemplate reactiveMongoTemplate(@Value("${spring.data.mongodb.uri}") String uri) {
        ConnectionString cs = new ConnectionString(uri);
        return new ReactiveMongoTemplate(MongoClients.create(cs), cs.getDatabase());
    }
}
