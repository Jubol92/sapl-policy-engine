/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.config.filesystem;

import static io.sapl.util.filemonitoring.FileMonitorUtil.monitorDirectory;
import static io.sapl.util.filemonitoring.FileMonitorUtil.resolveHomeFolderIfPresent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class FileSystemVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String watchDir;
    private final Flux<Optional<PolicyDecisionPointConfiguration>> configFlux;
    private final Disposable monitorSubscription;

    public FileSystemVariablesAndCombinatorSource(String configurationPath) {
        watchDir = resolveHomeFolderIfPresent(configurationPath);
        log.info("Monitor folder for config: {}", watchDir);
        Flux<FileEvent> monitoringFlux = monitorDirectory(watchDir,
                file -> CONFIG_FILE_GLOB_PATTERN.equals(file.getName()));
        configFlux = monitoringFlux.scan(loadConfig(), (__, fileEvent) -> processWatcherEvent(fileEvent))
                .distinctUntilChanged().share()
                .cache(1);
        monitorSubscription = Flux.from(configFlux).subscribe();
    }

    private Optional<PolicyDecisionPointConfiguration> loadConfig() {
        Path configurationFile = Paths.get(watchDir, CONFIG_FILE_GLOB_PATTERN);
        log.info("Loading config from: {}", configurationFile.toAbsolutePath());
        if (Files.notExists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
            // If file does not exist, return default configuration
            log.info("No config file present. Use default config.");
            return Optional.of(new PolicyDecisionPointConfiguration());
        }
        try {
            return Optional.of(objectMapper.readValue(configurationFile.toFile(), PolicyDecisionPointConfiguration.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Flux<Optional<CombiningAlgorithm>> getCombiningAlgorithm() {
        return Flux.from(configFlux)
                .switchMap(config -> config.map(policyDecisionPointConfiguration -> Flux.just(Optional
                        .of(CombiningAlgorithmFactory.getCombiningAlgorithm(policyDecisionPointConfiguration.getAlgorithm()))))
                        .orElseGet(() -> Flux.just(Optional.empty())));
    }

    @Override
    public Flux<Optional<Map<String, JsonNode>>> getVariables() {
        return Flux.from(configFlux).switchMap(config -> config.map(policyDecisionPointConfiguration -> Flux
                .just(Optional.of(policyDecisionPointConfiguration.getVariables())))
                .orElseGet(() -> Flux.just(Optional.empty())));
    }

    private Optional<PolicyDecisionPointConfiguration> processWatcherEvent(FileEvent fileEvent) {
        if (fileEvent instanceof FileDeletedEvent) {
            log.info("Configuration file deleted. Reverting to default config.");
            return Optional.of(new PolicyDecisionPointConfiguration());
        }
        // MODIFY or CREATED
        return loadConfig();
    }

    @Override
    public void dispose() {
        if (!monitorSubscription.isDisposed())
            monitorSubscription.dispose();
    }

}