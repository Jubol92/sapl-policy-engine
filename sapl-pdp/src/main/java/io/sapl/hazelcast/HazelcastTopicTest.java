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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/***
 * Simple test message to send when a hazelcast node is started.
 * Will be deleted later
 */
@Component
@ConditionalOnBean(HazelcastInstance.class)
public class HazelcastTopicTest {

    public HazelcastTopicTest(HazelcastInstance hazelcastInstance) {

        ITopic<String> topic = hazelcastInstance.getTopic("test-topic");

        topic.addMessageListener(msg -> System.out.println("Received: " + msg.getMessageObject()));

        new Thread(() -> {
            try {
                // wait 10 seconds after the start of a node and publish the test message
                Thread.sleep(10000);
                topic.publish("Node" + hazelcastInstance.getName() + " is up");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
