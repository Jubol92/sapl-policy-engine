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

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClients;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.MongoAttributeRepository;
import io.sapl.attributes.broker.repository.PostgresAttributeRepository;
import io.sapl.attributes.broker.repository.RedisAttributeRepository;
import io.sapl.pdp.configuration.source.PdpIdValidator;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@UtilityClass
public class AttributeRepositoryFactory {

    public AttributeRepository create(ObjectValue config, String pdpId) {
        PdpIdValidator.validatePdpId(pdpId);
        val type = str(config, "type");
        return switch (type != null ? type : "") {
        case "postgres" -> {
            val cf = ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER, "postgresql")
                    .option(HOST, Objects.requireNonNull(str(config, "host"))).option(PORT, num(config, "port"))
                    .option(USER, Objects.requireNonNull(str(config, "username")))
                    .option(PASSWORD, Objects.requireNonNull(str(config, "password")))
                    .option(DATABASE, Objects.requireNonNull(str(config, "database"))).build());
            yield new PostgresAttributeRepository(DatabaseClient.create(cf), cf, pdpId);
        }
        case "mongo"    -> {
            val host     = str(config, "host");
            val port     = num(config, "port");
            val database = str(config, "database");
            val username = str(config, "username");
            val password = str(config, "password");
            val authDb   = str(config, "authDatabase");
            val creds    = username == null || username.isBlank() ? ""
                    : encode(username) + ":" + encode(password) + "@";
            val auth     = username == null || username.isBlank() ? ""
                    : "?authSource=" + (authDb != null ? authDb : database);
            val cs       = new ConnectionString("mongodb://" + creds + host + ":" + port + "/" + database + auth);
            yield new MongoAttributeRepository(
                    new ReactiveMongoTemplate(MongoClients.create(cs), Objects.requireNonNull(cs.getDatabase())),
                    pdpId);
        }
        case "redis"    -> {
            val host     = str(config, "host");
            val port     = num(config, "port");
            val password = str(config, "password");
            val db       = num(config, "database");
            val builder  = RedisURI.Builder.redis(host, port).withDatabase(db);
            if (password != null && !password.isBlank()) {
                builder.withPassword(password.toCharArray());
            }
            yield new RedisAttributeRepository(RedisClient.create(builder.build()), pdpId);
        }
        default         -> new InMemoryAttributeRepository();
        };
    }

    private String str(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof TextValue(String value) ? value : null;
    }

    private int num(ObjectValue obj, String key) {
        val v = obj.get(key);
        return v instanceof NumberValue(java.math.BigDecimal value) ? value.intValue() : 0;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
