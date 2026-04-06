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
package io.sapl.hazelcast;

import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.model.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
public class HazelcastAttributeListener {

    private final AttributeDistributionService distribution;
    private final AttributeRepository          repository;
    private final HazelcastNodeId              nodeId;

    public HazelcastAttributeListener(AttributeDistributionService distribution,
            AttributeRepository repository,
            HazelcastNodeId nodeId) {
        this.distribution = distribution;
        this.repository   = repository;
        this.nodeId       = nodeId;
    }

    @PostConstruct
    public void init() {
        log.info("Subscribing to Hazelcast topic on node {}", nodeId.getNodeId());

        distribution.subscribe(message -> {
            PublishAttributeEvent event = message.getMessageObject();

            log.info("Received attribute event on node {}", nodeId.getNodeId());

            if (event.getNodeId().equals(nodeId.getNodeId())) {
                return;
            }

            repository.publishAttribute(Value.of(event.getEntity()), event.getAttributeName(), List.of(),
                    Value.of(event.getValue()), Duration.ofSeconds(event.getTtl()),
                    AttributeRepository.TimeOutStrategy.valueOf(event.getStrategy())).subscribe();
        });
    }
}
