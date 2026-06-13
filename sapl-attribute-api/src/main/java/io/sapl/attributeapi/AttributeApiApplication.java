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
package io.sapl.attributeapi;

import io.sapl.pdp.configuration.AttributeConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// Without these excludes, Spring Boot's generic Mongo/R2DBC autoconfiguration
// eagerly tries to build a MongoClient/ConnectionFactory and fails at startup
// even when io.sapl.attributes.storage selects a different backend.
// sapl-pdp's AttributeConfiguration provides its own beans for the active
// backend only. See SaplNodeApplication for the matching exclude list.
@SpringBootApplication(excludeName = { "io.sapl.spring.data.mongo.config.SaplMongoReactiveAutoConfiguration",
        "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
        "org.springframework.boot.mongodb.autoconfigure.MongoReactiveAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveRepositoriesAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
        "org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryAutoConfiguration",
        "org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration",
        "org.springframework.boot.r2dbc.autoconfigure.health.ConnectionFactoryHealthContributorAutoConfiguration" })
@Import({ AttributeConfiguration.class })
public class AttributeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttributeApiApplication.class, args);
    }
}
