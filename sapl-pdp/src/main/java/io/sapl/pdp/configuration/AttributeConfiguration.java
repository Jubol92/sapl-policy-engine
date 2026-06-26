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
import io.lettuce.core.RedisURI;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.MongoAttributeRepository;
import io.sapl.attributes.broker.repository.PostgresAttributeRepository;
import io.sapl.attributes.broker.repository.RedisAttributeRepository;
import io.sapl.attributes.libraries.UserPolicyInformationPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@SuppressWarnings("unused")
@Configuration
@EnableConfigurationProperties(AttributeStorageProperties.class)
public class AttributeConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "heap", matchIfMissing = true)
    public AttributeRepository heapAttributeRepository() {
        return new InMemoryAttributeRepository();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public AttributeRepository postgresAttributeRepository(DatabaseClient client, ConnectionFactory connection) {
        return new PostgresAttributeRepository(client, connection);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public AttributeRepository mongoAttributeRepository(ReactiveMongoTemplate template) {
        return new MongoAttributeRepository(template);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    public RedisClient redisClient(AttributeStorageProperties properties) {
        val redis   = properties.getRedis();
        val builder = RedisURI.Builder.redis(redis.getHost(), redis.getPort()).withDatabase(redis.getDatabase());
        if (redis.getPassword() != null && !redis.getPassword().isBlank()) {
            builder.withPassword(redis.getPassword().toCharArray());
        }
        return RedisClient.create(builder.build());
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    public AttributeRepository redisAttributeRepository(RedisClient client) {
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
    public ConnectionFactory connectionFactory(AttributeStorageProperties properties) {
        val postgres = properties.getPostgres();
        return ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER, "postgresql")
                .option(HOST, postgres.getHost()).option(PORT, postgres.getPort()).option(USER, postgres.getUsername())
                .option(PASSWORD, postgres.getPassword()).option(DATABASE, postgres.getDatabase()).build());
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public ReactiveMongoTemplate reactiveMongoTemplate(AttributeStorageProperties properties) {
        val mongo       = properties.getMongo();
        val credentials = mongo.getUsername() == null || mongo.getUsername().isBlank() ? ""
                : encode(mongo.getUsername()) + ":" + encode(mongo.getPassword()) + "@";
        val authSource  = mongo.getUsername() == null || mongo.getUsername().isBlank() ? ""
                : "?authSource=" + mongo.getAuthDatabase();
        val cs          = new ConnectionString("mongodb://" + credentials + mongo.getHost() + ":" + mongo.getPort()
                + "/" + mongo.getDatabase() + authSource);
        return new ReactiveMongoTemplate(MongoClients.create(cs), cs.getDatabase());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
