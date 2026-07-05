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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Backend-agnostic API tests. Each subclass wires a different
 * AttributeRepository implementation and runs this full test suite against it.
 */
abstract class AbstractAttributeApiTests {

    @LocalServerPort
    int port;

    protected WebTestClient webClient;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        cleanRepository();
    }

    /** Subclasses clean their backend between tests. */
    protected void cleanRepository() {
    }

    @Test
    @DisplayName("POST /api/attributes/{name} returns 201")
    void publishGlobalAttribute() {
        webClient.post().uri("/api/attributes/sapl.test.role").contentType(MediaType.APPLICATION_JSON).bodyValue("""
                { "value": "test_1",
                  "ttl": 60
                 }
                """).exchange().expectStatus().isCreated();
    }

    @Test
    @DisplayName("POST /api/attributes/sapl.test/{name} returns 201")
    void publishAttributeWithEntity() {
        webClient.post().uri("/api/attributes/sapl.test/sapl.test.role").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        { "value": "test_2",
                          "ttl": 60
                          }
                        """).exchange().expectStatus().isCreated();
    }

    @Test
    @DisplayName("POST /api/attributes/sapl.test/{name} returns error for unqualified name")
    void publishAttributeWithInvalidAttributeName() {
        webClient.post().uri("/api/attributes/sapl.test/sapl").contentType(MediaType.APPLICATION_JSON).bodyValue("""
                { "value": "test_3",
                  "ttl": 60
                 }
                """).exchange().expectStatus().isBadRequest().expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("fully qualified name"));
    }

    @Test
    @DisplayName("GET /api/attributes/sapl.test/{name} returns test_4 value")
    void getGlobalAttribute() {
        webClient.post().uri("/api/attributes/sapl.test.deletion").contentType(MediaType.APPLICATION_JSON).bodyValue("""
                { "value": "test_4",
                  "ttl": 60
                  }
                """).exchange().expectStatus().isCreated();

        webClient.get().uri("/api/attributes/sapl.test.deletion").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$").isEqualTo("test_4");
    }

    @Test
    @DisplayName("DELETE /api/attributes/sapl.test/{name} returns 201")
    void publishAndDeleteAttribute() {
        webClient.post().uri("/api/attributes/sapl.test/sapl.test.publishAndDelete")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("""
                        { "value": "test_5",
                          "ttl": 60
                          }
                        """).exchange().expectStatus().isCreated();

        webClient.get().uri("/api/attributes/sapl.test/sapl.test.publishAndDelete").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$").isEqualTo("test_5");

        webClient.delete().uri("/api/attributes/sapl.test/sapl.test.publishAndDelete").exchange().expectStatus()
                .isNoContent();
    }

    @Test
    @DisplayName("TTL expires for /api/attributes/sapl.test/{name} and shows no content")
    void ttlExpires() throws InterruptedException {
        webClient.post().uri("/api/attributes/sapl.test/sapl.test.ttlExpired").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        { "value": "test_6",
                          "ttl": 1
                          }
                        """).exchange().expectStatus().isCreated();

        Thread.sleep(2000);

        webClient.get().uri("/api/attributes/sapl.test/sapl.test.ttlExpired").exchange().expectStatus().isNotFound();
    }
}
