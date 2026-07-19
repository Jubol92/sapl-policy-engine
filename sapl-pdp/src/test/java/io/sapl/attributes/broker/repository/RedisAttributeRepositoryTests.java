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

import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.RedisClient;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("RedisAttributeRepository")
class RedisAttributeRepositoryTests {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    private RedisClient              client;
    private RedisAttributeRepository repository;
    private final List<Value>        received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        client     = RedisClient.create(redis.getRedisURI());
        repository = new RedisAttributeRepository(client, "test-tenant");
        received.clear();
    }

    @AfterEach
    void tearDown() {
        client.connect().sync().flushall();
        repository.close();
    }

    private static RepositoryKey key(String name) {
        return new RepositoryKey(null, name, List.of());
    }

    private static AttributeFinderInvocation invocation(String name) {
        return new AttributeFinderInvocation("default", name, List.of(), Duration.ofSeconds(1), Duration.ofMillis(100),
                Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    @Nested
    @DisplayName("when a value is published")
    class WhenValueIsPublished {

        @Test
        @DisplayName("then observe returns it immediately")
        void thenGetReturnsIt() {
            repository.publish(key("sapl.test.attr"), Value.of("test"));
            repository.observe(invocation("sapl.test.attr"), received::add);

            assertThat(received.getFirst()).isEqualTo(Value.of("test"));
        }

        @Test
        @DisplayName("value survives a restart")
        void thenItSurvivesRestart() {
            repository.publish(key("sapl.test.persist"), Value.of(42L));

            val client2 = RedisClient.create(redis.getRedisURI());
            try (val repo2 = new RedisAttributeRepository(client2, "test-tenant")) {
                repo2.observe(invocation("sapl.test.persist"), received::add);
                assertThat(received.getFirst()).isEqualTo(Value.of(42L));
            }
        }
    }

    @Nested
    @DisplayName("when a value is removed")
    class WhenValueIsRemoved {

        @Test
        @DisplayName("then observer receives UNDEFINED")
        void thenGetReturnsUndefined() {
            repository.publish(key("sapl.test.remove"), Value.of("deleteIt"));
            repository.observe(invocation("sapl.test.remove"), received::add);
            repository.remove(key("sapl.test.remove"));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.getLast().equals(Value.UNDEFINED));
        }
    }

    @Nested
    @DisplayName("overwrite existing attribute")
    class WhenAttributeIsOverwritten {

        @Test
        @DisplayName("observer receives the new value")
        void thenObserverReceivesNewValue() {
            repository.publish(key("sapl.test.overwrite"), Value.of("1"), Duration.ofSeconds(120));
            repository.observe(invocation("sapl.test.overwrite"), received::add);
            assertThat(received.getFirst()).isEqualTo(Value.of("1"));

            repository.publish(key("sapl.test.overwrite"), Value.of("2"), Duration.ofSeconds(120));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.getLast().equals(Value.of("2")));
        }
    }

    @Nested
    @DisplayName("when ttl expires")
    class WhenTtlExpires {

        @Test
        @DisplayName("after TTL expires, a fresh observe returns UNDEFINED")
        void thenObserverReceivesUndefinedAfterExpiry() throws InterruptedException {
            repository.publish(key("sapl.test.ttl"), Value.of("temp"), Duration.ofSeconds(1));
            // Redis expires keys natively without notifying existing observers.
            // Wait for expiry, then verify via a fresh observe delivering the initial
            // value.
            Thread.sleep(2_000);
            repository.observe(invocation("sapl.test.ttl"), received::add);
            assertThat(received.getFirst()).isEqualTo(Value.UNDEFINED);
        }
    }

    @Nested
    @DisplayName("pub/sub observer")
    class PubSubObserver {

        @Test
        @DisplayName("observer is notified when value is published from another instance")
        void observerNotifiedFromOtherInstance() {
            repository.observe(invocation("sapl.test.observe"), received::add);
            received.clear(); // discard initial UNDEFINED

            val client2 = RedisClient.create(redis.getRedisURI());
            try (val repo2 = new RedisAttributeRepository(client2, "test-tenant")) {
                repo2.publish(key("sapl.test.observe"), Value.of("from-other-node"));
            }

            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> received.contains(Value.of("from-other-node")));
        }

        @Test
        @DisplayName("observer is notified with UNDEFINED when value is removed")
        void observerNotifiedOnRemove() {
            repository.publish(key("sapl.test.observeRemove"), Value.of("initial"));
            repository.observe(invocation("sapl.test.observeRemove"), received::add);
            received.clear(); // discard initial "initial"

            repository.remove(key("sapl.test.observeRemove"));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.contains(Value.UNDEFINED));
        }
    }
}
