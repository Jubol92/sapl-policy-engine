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
package io.sapl.attributes.push;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeRepository.TimeOutStrategy;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import io.sapl.hazelcast.AttributeDistributionService;
import io.sapl.hazelcast.HazelcastNodeId;
import io.sapl.hazelcast.PublishAttributeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/attributes")
public class AttributePushController {
    private final AttributeRepository repository;

    private final AttributeDistributionService distribution;
    private final HazelcastNodeId              nodeId;

    public AttributePushController(AttributeRepository repository,
            ObjectProvider<AttributeDistributionService> distribution,
            HazelcastNodeId nodeId) {
        this.repository   = repository;
        this.distribution = distribution.getIfAvailable();
        this.nodeId       = nodeId;
    }

    // Request muss dem DTO aus PushRequest entsprechen
    @PostMapping("/publish")
    public Mono<String> publish(@RequestBody PushRequest request) {
        // Either empty or set
        Value  entity = request.getEntity() == null ? null : Value.of(request.getEntity());
        String name   = request.getAttributeName();
        Value  value  = convertValue(request.getAttributeValue());

        List<Value> arguments = request.getArguments() == null ? List.of()
                : request.getArguments().stream().map(this::convertValue).toList();

        Duration ttl = request.getTtl() == null ? Duration.ofHours(1) : Duration.ofSeconds(request.getTtl());

        TimeOutStrategy strategy = request.getStrategy() == null ? TimeOutStrategy.REMOVE
                : TimeOutStrategy.valueOf(request.getStrategy());

        String message = "Publishing to repository " + repository.hashCode();
        log.debug(message);

        return repository.publishAttribute(entity, name, arguments, value, ttl, strategy).doOnSuccess(v -> {
            log.info("Distribution: {}", distribution.toString());
            PublishAttributeEvent event = new PublishAttributeEvent();
            event.setNodeId(nodeId.getNodeId());
            event.setEntity(request.getEntity());
            event.setAttributeName(name);
            event.setValue(request.getAttributeValue());
            event.setTtl(ttl.getSeconds());
            event.setStrategy(strategy.name());
            distribution.publish(event);
        }).thenReturn("Attribute published to repository");
    }

    @DeleteMapping("/delete/{entity}/{attribute}")
    public Mono<String> deleteAttribute(@PathVariable String entity, @PathVariable String attribute) {
        return repository.removeAttribute(convertValue(entity), attribute)
                .thenReturn("Attribute remove from repository");
    }

    @DeleteMapping("/delete/{attribute}")
    public Mono<String> deleteAttribute(@PathVariable String attribute) {
        return repository.removeAttribute(attribute).thenReturn("Attribute remove from repository");
    }

    @GetMapping("/entity/{entity}")
    public Flux<PersistedAttribute> findAttributeByEntity(@PathVariable String entity) {
        return repository.getAttributeForEntity(Value.of(entity));
    }

    @GetMapping("/findAll")
    public Flux<Map.Entry<AttributeKey, PersistedAttribute>> findAllAttributes() {
        return repository.getAllAttributes();
    }

    // Internal helper method the right Value object
    private Value convertValue(Object input) {
        switch (input) {
        case null      -> {
            return Value.NULL;
        }
        case Boolean b -> {
            return Value.of(b);
        }
        case Integer i -> {
            return Value.of(i.longValue());
        }
        case Long l    -> {
            return Value.of(l);
        }
        case Double v  -> {
            return Value.of(v);
        }
        case String s  -> {
            return Value.of(s);
        }
        default        -> {
            return null;
        }
        }
    }
}
