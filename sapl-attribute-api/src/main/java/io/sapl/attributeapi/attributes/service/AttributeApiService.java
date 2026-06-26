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
package io.sapl.attributeapi.attributes.service;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributeapi.attributes.backend.AttributeSignature;
import io.sapl.attributeapi.attributes.backend.AttributeStore;
import io.sapl.attributeapi.attributes.dto.AttributePublishRequest;
import io.sapl.attributeapi.attributes.dto.AttributeValueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
public class AttributeApiService {
    private final AttributeStore store;

    public void publish(String entity, String attribute, AttributePublishRequest body) {
        List<Value> arguments   = body.getArguments() == null ? List.of()
                : body.getArguments().stream().map(ValueJsonMarshaller::fromJsonNode).toList();
        Value       entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        Value       value       = ValueJsonMarshaller.fromJsonNode(body.getValue());
        Long        ttl         = body.getTtl();

        var sig = new AttributeSignature(entityValue, attribute, arguments);
        if (ttl == null || ttl <= 0) {
            store.publish(sig, value);
        } else {
            store.publish(sig, value, Duration.ofSeconds(ttl));
        }
    }

    public void delete(String entity, String attribute, List<String> rawArgs) {
        List<Value> arguments   = rawArgs == null ? List.of() : rawArgs.stream().map(this::fromString).toList();
        Value       entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;

        store.remove(new AttributeSignature(entityValue, attribute, arguments));
    }

    public AttributeValueResponse get(String entity, String attribute, List<String> rawArgs) {
        List<Value> arguments   = rawArgs == null ? List.of() : rawArgs.stream().map(this::fromString).toList();
        Value       entityValue = entity != null && !entity.isBlank() ? Value.of(entity) : null;
        Value       value       = store.get(new AttributeSignature(entityValue, attribute, arguments));

        if (value == Value.UNDEFINED)
            throw new NoSuchElementException();

        return new AttributeValueResponse(ValueJsonMarshaller.toJsonNodeLenient(value));
    }

    // Converts a query-parameter string into a SAPL value.
    // Falls back to a plain text value if the string is not valid JSON.
    private Value fromString(String data) {
        Value parsed = ValueJsonMarshaller.json(data);
        return parsed instanceof ErrorValue ? Value.of(data) : parsed;
    }
}
