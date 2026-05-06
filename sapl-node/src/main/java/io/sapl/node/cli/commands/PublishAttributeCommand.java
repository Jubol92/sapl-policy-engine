package io.sapl.node.cli.commands;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import reactor.netty.http.HttpProtocol;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Command(name = "publish", mixinStandardHelpOptions = true, description = "Publish an attribute into the attribute repository of a running SAPL node")
public class PublishAttributeCommand extends BaseAttributeCommand {

    private static final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.H2C)))
            .build();

    // Adds the generic commands for the storage
    @Mixin
    StorageTransportMixin storage;

    @Option(names = "--entity", required = true)
    String entity;

    @Option(names = "--name", required = true)
    String name;

    @Option(names = "--value", required = true)
    String value;

    @Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Option(names = "--ttl", description = "Time to live in seconds. The attribute expires after this duration.", defaultValue = "2000")
    Long ttl;

    @Option(names = "--strategy", description = "Timeout strategy after TTL expires: REMOVE or BECOME_UNDEFINED", defaultValue = "REMOVE")
    String strategy;

    @Override
    public Integer call() {
        if (storage.transport.url != null) {
            return publishViaApi();
        }
        return publishViaProvider();
    }

    private Integer publishViaApi() {
        String json = """
                {
                    "entity": "%s",
                    "attributeName": "%s",
                    "attributeValue": "%s",
                    "arguments": [],
                    "ttl": %s,
                    "strategy": "%s"
                }
                """.formatted(entity, name, value, ttl, strategy);

        var response = webClient.post().uri(storage.transport.url + "/api/attributes")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(json).retrieve().toEntity(String.class).block();

        return response != null && response.getStatusCode().value() == 200 ? 0 : 1;
    }

    private Integer publishViaProvider() {
        try {
            return publishToStorage(storage.createStorage());
        } catch (IllegalArgumentException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return 1;
        }
    }

    private Integer publishToStorage(AttributeStorage attributeStorage) {
        try {
            var args      = arguments.stream().filter(s -> !s.isEmpty()).map(this::parseArgument).toList();
            var key       = new AttributeKey(Value.of(entity), name, args);
            var duration  = Duration.ofSeconds(ttl);
            var persisted = new PersistedAttribute(Value.of(value), Instant.now(), duration,
                    AttributeRepository.TimeOutStrategy.valueOf(strategy), Instant.now().plus(duration));

            attributeStorage.put(key, persisted).block();
            return 0;
        } catch (Exception e) {
            spec.commandLine().getErr().println("Failed to publish attribute: " + e.getMessage());
            return 1;
        }
    }
}
