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

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class HazelcastConfiguration {

    @Value("${io.sapl.hazelcast.enabled:false}")
    private boolean enabled;

    @Value("${io.sapl.hazelcast.cluster-name:sapl-cluster}")
    private String clusterName;

    @Value("${io.sapl.hazelcast.topic-name:sapl-attribute-events}")
    private String topicName;

    @Value("${io.sapl.hazelcast.network.port:5701}")
    private int port;

    @Bean
    public HazelcastInstance hazelcastInstance() {
        if (!enabled) {
            return Hazelcast.newHazelcastInstance(new Config());
        }

        Config config = new Config();
        config.setClusterName(clusterName);

        NetworkConfig network = config.getNetworkConfig();
        network.setPort(port);
        network.setPortAutoIncrement(true);

        JoinConfig join = network.getJoin();
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);

        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1:5701").addMember("127.0.0.1:5702")
                .addMember("127.0.0.1:5703");

        return Hazelcast.newHazelcastInstance(config);
    }
}
