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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Value("${io.sapl.hazelcast.discovery.mode:multicast}")
    private String mode;

    @Bean
    @ConditionalOnProperty(name = "io.sapl.hazelcast.enabled", havingValue = "true")
    public HazelcastInstance hazelcastInstance() {
        /*
         * if (!enabled) {
         * return Hazelcast.newHazelcastInstance(new Config());
         * }
         */

        // Create the Hazelcast config and sets a cluster name. Other nodes identify
        // over the same name
        Config config = new Config();
        config.setClusterName(clusterName);

        // Creates the network config. Starts with the port 5701 (default) and allows
        // the increment of a port
        // Auto increment is important if the nodes are running on the same machine
        // (e.g. testing purposes)
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(port);
        network.setPortAutoIncrement(true);

        JoinConfig join = network.getJoin();
        join.getAutoDetectionConfig().setEnabled(false);

        // todo: Multicast group and port configurable
        if ("multicast".equals(mode)) {
            join.getMulticastConfig().setEnabled(true).setMulticastGroup("224.2.2.3").setMulticastPort(54327);
        } else {
            join.getMulticastConfig().setEnabled(false);
        }

        if ("tcpip".equals(mode)) {
            join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1:5701").addMember("127.0.0.1:5702")
                    .addMember("127.0.0.1:5703");
        } else {
            join.getTcpIpConfig().setEnabled(false);
        }

        return Hazelcast.newHazelcastInstance(config);
    }
}
