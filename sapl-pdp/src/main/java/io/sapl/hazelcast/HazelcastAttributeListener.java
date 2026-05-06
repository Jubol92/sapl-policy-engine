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

import com.hazelcast.core.HazelcastInstance;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeRepository.TimeOutStrategy;
import io.sapl.api.model.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@ConditionalOnBean(HazelcastInstance.class)
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

    // Executed after Bean creation and dependency injection
    // Method to register to the Hazelcast topic
    @PostConstruct
    public void init() {
        log.info("Subscribing to Hazelcast topic on node {}", nodeId.getNodeId());

        // Registration + Event publishing
        distribution.subscribe(message -> {
            PublishAttributeEvent event = message.getMessageObject();

            log.info("Received attribute event on node {}", nodeId.getNodeId());

            // Do no actions when the event was from this node
            if (event.getNodeId().equals(nodeId.getNodeId())) {
                return;
            }

            // Publish the event into the repository
            repository.publishAttribute(Value.of(event.getEntity()), event.getAttributeName(), List.of(),
                    Value.of(event.getValue()), Duration.ofSeconds(event.getTtl()),
                    TimeOutStrategy.valueOf(event.getStrategy())).subscribe();
        });
    }
}
