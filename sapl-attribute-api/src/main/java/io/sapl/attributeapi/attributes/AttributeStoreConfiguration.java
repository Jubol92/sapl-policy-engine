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
package io.sapl.attributeapi.attributes;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClients;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.backend.MongoAttributeStore;
import io.sapl.attributeapi.attributes.backend.PostgresAttributeStore;
import io.sapl.attributeapi.attributes.backend.RedisAttributeStore;
import lombok.val;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@Configuration
@EnableConfigurationProperties(AttributeStorageProperties.class)
public class AttributeStoreConfiguration {

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public ConnectionFactory connectionFactory(AttributeStorageProperties properties) {
        val p = properties.getPostgres();
        return ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER, "postgresql")
                .option(HOST, p.getHost()).option(PORT, p.getPort()).option(USER, p.getUsername())
                .option(PASSWORD, p.getPassword()).option(DATABASE, p.getDatabase()).build());
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "postgres")
    public AttributeStore postgresAttributeStore(DatabaseClient client) {
        return new PostgresAttributeStore(client);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public ReactiveMongoTemplate reactiveMongoTemplate(AttributeStorageProperties properties) {
        val m           = properties.getMongo();
        val credentials = m.getUsername() == null || m.getUsername().isBlank() ? ""
                : encode(m.getUsername()) + ":" + encode(m.getPassword()) + "@";
        val authSource  = m.getUsername() == null || m.getUsername().isBlank() ? ""
                : "?authSource=" + m.getAuthDatabase();
        val cs          = new ConnectionString(
                "mongodb://" + credentials + m.getHost() + ":" + m.getPort() + "/" + m.getDatabase() + authSource);
        return new ReactiveMongoTemplate(MongoClients.create(cs), cs.getDatabase());
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "mongo")
    public AttributeStore mongoAttributeStore(ReactiveMongoTemplate template) {
        return new MongoAttributeStore(template);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    public RedisClient redisClient(AttributeStorageProperties properties) {
        val r       = properties.getRedis();
        val builder = RedisURI.Builder.redis(r.getHost(), r.getPort()).withDatabase(r.getDatabase());
        if (r.getPassword() != null && !r.getPassword().isBlank()) {
            builder.withPassword(r.getPassword().toCharArray());
        }
        return RedisClient.create(builder.build());
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue = "redis")
    public AttributeStore redisAttributeStore(RedisClient client) {
        return new RedisAttributeStore(client);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
