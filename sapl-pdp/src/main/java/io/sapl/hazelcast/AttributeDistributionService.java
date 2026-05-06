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
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/***
 * Distribution service class for several topics within the hazelcast cluster.
 */
@Slf4j
@Service
@ConditionalOnBean(HazelcastInstance.class)
public class AttributeDistributionService {
    private final HazelcastInstance hazelcast;

    // Important: there should be only one hazelcast instance
    public AttributeDistributionService(HazelcastInstance hazelcast) {

        this.hazelcast = hazelcast;
    }

    // Gets the topic from the cluster
    // https://docs.hazelcast.com/hazelcast/5.6/data-structures/topic
    // To-Do: enable globalOrderEnabled
    private ITopic<PublishAttributeEvent> topic() {
        if (hazelcast == null) {
            return null;
        }
        // To-Do: No static topic name in the class
        String topicName = "sapl-attribute-events";
        return hazelcast.getTopic(topicName);
    }

    // Publishes event into the cluster
    public void publish(PublishAttributeEvent event) {
        ITopic<PublishAttributeEvent> topic = topic();
        if (topic != null) {
            log.info("Publishing event to Hazelcast topic");
            /*
             * 1. Serialize event
             * 2. Send to Hazelcast cluster
             * 3. Send the event to all other nodes that are registered to this topic
             */
            topic.publish(event);
        } else {
            log.error("Hazelcast topic is null. Cannot publish");
        }
    }

    // Registers a listener for the given topic
    // Topic = communication channel, Listener = communication logic
    public void subscribe(MessageListener<PublishAttributeEvent> listener) {
        ITopic<PublishAttributeEvent> topic = topic();
        if (topic != null) {
            log.debug("Subscribing to hazelcast topic {}", topic.getName());
            topic.addMessageListener(listener);
        } else {
            log.error("Cannot subscribe to topic because topic is null");
        }
    }
}
