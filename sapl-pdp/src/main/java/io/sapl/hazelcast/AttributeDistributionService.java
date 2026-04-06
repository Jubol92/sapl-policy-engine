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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AttributeDistributionService {
    private final HazelcastInstance hazelcast;

    public AttributeDistributionService(HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
    }

    private ITopic<PublishAttributeEvent> topic() {
        if (hazelcast == null) {
            return null;
        }
        String topicName = "sapl-attribute-events";
        return hazelcast.getTopic(topicName);
    }

    public void publish(PublishAttributeEvent event) {
        ITopic<PublishAttributeEvent> topic = topic();
        if (topic != null) {
            System.out.println("Publishing event to Hazelcast topic");
            topic.publish(event);
        } else {
            System.out.println("Hazelcast topic is NULL");
        }
    }

    public void subscribe(MessageListener<PublishAttributeEvent> listener) {
        ITopic<PublishAttributeEvent> topic = topic();
        if (topic != null) {
            System.out.println("Subscribing to Hazelcast topic");
            topic.addMessageListener(listener);
        } else {
            System.out.println("Cannot subscribe, topic NULL");
        }
    }
}
