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
package io.sapl.attributeapigui.client;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.sapl.attributeapigui.connection.ConnectionSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@VaadinSessionScope
public class AttributeApiClient {

    private final ConnectionSettings settings;
    private final RestClient         client = RestClient.create();

    public AttributeApiClient(ConnectionSettings settings) {
        this.settings = settings;
    }

    public Optional<Object> getAttribute(String entity, String name) {
        checkConfiguration();

        try {
            var request = (entity == null || entity.isBlank())
                    ? client.get().uri(settings.getBaseUrl() + "/api/attributes/{name}", name)
                    : client.get().uri(settings.getBaseUrl() + "/api/attributes/{entity}/{name}", entity, name);

            var value = request.headers(this::addAuthorization).retrieve().body(Object.class);

            return Optional.ofNullable(value);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    // todo: arguments need to be deleted as well
    public boolean deleteAttribute(String entity, String name) {
        checkConfiguration();
        try {
            var request = (entity == null || entity.isBlank())
                    ? client.delete().uri(settings.getBaseUrl() + "/api/attributes/{name}", name)
                    : client.delete().uri(settings.getBaseUrl() + "/api/attributes/{entity}/{name}", entity, name);

            request.headers(this::addAuthorization).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    public List<Map<String, Object>> getAllAttributes() {
        checkConfiguration();

        return client.get().uri(settings.getBaseUrl() + "/api/attributes").headers(this::addAuthorization).retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private void checkConfiguration() {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Not connected - configure a connection on the Settings view first.");
        }
    }

    private void addAuthorization(HttpHeaders headers) {
        switch (settings.getMode()) {
        case NONE -> {
            // keine Authentifizierung
        }

        case BASIC -> headers.setBasicAuth(settings.getUsername(), settings.getPassword());

        case API -> headers.setBearerAuth(settings.getApiKey());

        default -> throw new IllegalStateException("Unsupported authentication mode: " + settings.getMode());
        }
    }
}
