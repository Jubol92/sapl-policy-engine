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
package io.sapl.pdp.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.attributes.libraries.UserPolicyInformationPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@SuppressWarnings("unused")
@Configuration
@EnableConfigurationProperties(AttributeStorageProperties.class) // deprecated: remove later. not used anymore
public class AttributeConfiguration {

    // Shared fallback for observe() on an unknown configId. One instance for the whole process
    // lifetime, not one per call - InMemoryAttributeRepository starts a scheduler thread in its
    // constructor, so Map.getOrDefault(..., new InMemoryAttributeRepository()) would leak a
    // thread on every call because getOrDefault evaluates its default argument eagerly.
    private static final AttributeRepository EMPTY_REPOSITORY = new InMemoryAttributeRepository();

    /*
     * @Bean
     *
     * @Primary
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "heap", matchIfMissing = true)
     * public AttributeRepository heapAttributeRepository() {
     * return new InMemoryAttributeRepository();
     * }
     *
     * @Bean
     *
     * @Primary
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "postgres")
     * public AttributeRepository postgresAttributeRepository(DatabaseClient client,
     * ConnectionFactory connection) {
     * return new PostgresAttributeRepository(client, connection);
     * }
     *
     * @Bean
     *
     * @Primary
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "mongo")
     * public AttributeRepository mongoAttributeRepository(ReactiveMongoTemplate
     * template) {
     * return new MongoAttributeRepository(template);
     * }
     *
     * @Bean
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "redis")
     * public RedisClient redisClient(AttributeStorageProperties properties) {
     * val redis = properties.getRedis();
     * val builder = RedisURI.Builder.redis(redis.getHost(),
     * redis.getPort()).withDatabase(redis.getDatabase());
     * if (redis.getPassword() != null && !redis.getPassword().isBlank()) {
     * builder.withPassword(redis.getPassword().toCharArray());
     * }
     * return RedisClient.create(builder.build());
     * }
     *
     * @Bean
     *
     * @Primary
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "redis")
     * public AttributeRepository redisAttributeRepository(RedisClient client) {
     * return new RedisAttributeRepository(client);
     * }
     *
     * @Bean
     * public AttributeBroker attributeBroker(AttributeRepository repository) {
     * return
     * PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.
     * systemUTC(),
     * JsonMapper.builder().build(), true, List.of(new
     * UserPolicyInformationPoint()), repository);
     * }
     *
     * @Bean
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "postgres")
     * public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
     * return DatabaseClient.create(connectionFactory);
     * }
     *
     * @Bean
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "postgres")
     * public ConnectionFactory connectionFactory(AttributeStorageProperties
     * properties) {
     * val postgres = properties.getPostgres();
     * return
     * ConnectionFactories.get(ConnectionFactoryOptions.builder().option(DRIVER,
     * "postgresql")
     * .option(HOST, postgres.getHost()).option(PORT,
     * postgres.getPort()).option(USER, postgres.getUsername())
     * .option(PASSWORD, postgres.getPassword()).option(DATABASE,
     * postgres.getDatabase()).build());
     * }
     *
     * @Bean
     * public ObjectMapper objectMapper() {
     * ObjectMapper mapper = new ObjectMapper();
     * mapper.findAndRegisterModules();
     * return mapper;
     * }
     *
     * @Bean
     *
     * @ConditionalOnProperty(name = "io.sapl.attributes.storage", havingValue =
     * "mongo")
     * public ReactiveMongoTemplate reactiveMongoTemplate(AttributeStorageProperties
     * properties) {
     * val mongo = properties.getMongo();
     * val credentials = mongo.getUsername() == null ||
     * mongo.getUsername().isBlank() ? ""
     * : encode(mongo.getUsername()) + ":" + encode(mongo.getPassword()) + "@";
     * val authSource = mongo.getUsername() == null || mongo.getUsername().isBlank()
     * ? ""
     * : "?authSource=" + mongo.getAuthDatabase();
     * val cs = new ConnectionString("mongodb://" + credentials + mongo.getHost() +
     * ":" + mongo.getPort()
     * + "/" + mongo.getDatabase() + authSource);
     * return new ReactiveMongoTemplate(MongoClients.create(cs), cs.getDatabase());
     * }
     *
     * private static String encode(String value) {
     * return URLEncoder.encode(value, StandardCharsets.UTF_8);
     * }
     */

    /*
     * Builds the bean for the AttributeRepository. Contains two hash maps. One hash map from the configuration id to
     * the attribute repository and the other from the PDP id to the current configuration id. If there is a load or
     * remove event: create the repository over the factory.
     */
    @Bean
    @Primary
    // SuppressWarnings("resource"): The repository outlives this method, and it's stored in the cache and stored later.
    // TODO: Clarify if SuppressWarnings is ok!
    @SuppressWarnings("resource")
    public AttributeRepository attributeRepository(PDPConfigurationSource source) {
        var cache       = new ConcurrentHashMap<String, AttributeRepository>(); // configId → repo
        var pdpToConfig = new ConcurrentHashMap<String, String>();              // pdpId → current configId

        // Register the repository to the config change and removes the old repository
        source.subscribe(event -> {
            if (event instanceof ConfigurationEvent.NewConfiguration(io.sapl.api.pdp.configuration.PDPConfiguration configuration)) {
                val pdpId       = configuration.pdpId();
                val configId    = configuration.configurationId();
                val oldConfigId = pdpToConfig.put(pdpId, configId);

                if (oldConfigId != null && !oldConfigId.equals(configId)) {
                    Optional.ofNullable(cache.remove(oldConfigId)).ifPresent(AttributeRepository::close);
                }

                val repoNode = configuration.data().secrets().get("attributeRepository");

                cache.computeIfAbsent(configId,
                        k -> repoNode instanceof ObjectValue obj ? AttributeRepositoryFactory.create(obj, pdpId)
                                : new InMemoryAttributeRepository());
            } else if (event instanceof ConfigurationEvent.ConfigurationRemoved(String pdpId)) {

                val configId = pdpToConfig.remove(pdpId);
                Optional.ofNullable(cache.remove(configId)).ifPresent(AttributeRepository::close);
            }
        });

        // Anonymous repository instance for the bean during the runtime. Dependent on the cache map it returns the
        // right repository. The bean functions as proxy / router to route between x tenants to y backends.
        // Reason: there's only one repository bean for the whole process but x tenants with different config.
        // Otherwise, we would need a new spring context for each tenant or create a new bean
        // Own spring context: too much overhead (own security config, all the beans, ...)
        // New beans: cheaper, but the attribute broker gets a hard-wired object reference to the repository
        // as constructor injection. During the runtime the application references to this object. That's why
        // we're using a proxy
        // Only observe and close are routed. The objects within the map are built objects from the factory. For that
        // case publish/remove are never used because only the observe function is used within the pdp. At this point
        // the
        // router doesn't have the information of the repository key, entity etc. to call a publish/remove anyways
        return new AttributeRepository() {
            @Override
            public Registration observe(@NonNull AttributeFinderInvocation inv, @NonNull Consumer<Value> onValue) {
                return cache.getOrDefault(inv.configurationId(), EMPTY_REPOSITORY).observe(inv, onValue);
            }

            @Override
            public void publish(@NonNull RepositoryKey k, @NonNull Value v) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void publish(@NonNull RepositoryKey k, @NonNull Value v, @NonNull Duration ttl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void remove(@NonNull RepositoryKey k) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                cache.values().forEach(AttributeRepository::close);
            }
        };
    }

    // the broker bean to load all PIP's annotated with PolicyInformationPoint
    @Bean
    public AttributeBroker attributeBroker(AttributeRepository repository, ApplicationContext ctx) {
        val pipBeans = Arrays.stream(ctx.getBeanNamesForAnnotation(PolicyInformationPoint.class)).map(ctx::getBean)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        pipBeans.add(new UserPolicyInformationPoint());

        return PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.systemUTC(),
                JsonMapper.builder().build(), true, pipBeans, repository);
    }

    // todo: maybe deprecated or still used to load jackson instances?
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

}
