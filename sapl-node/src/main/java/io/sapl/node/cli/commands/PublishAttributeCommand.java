package io.sapl.node.cli.commands;

import io.sapl.api.attributes.AttributeRepository.TimeOutStrategy;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import org.springframework.http.MediaType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Command(name = "publish", mixinStandardHelpOptions = true, description = "Publish an attribute into the attribute repository of a running SAPL node")
public class PublishAttributeCommand extends BaseAttributeCommand {

    @Option(names = "--entity", required = true, description = "The subject or resource the attribute belongs to")
    String entity;

    @Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @Option(names = "--value", required = true, description = "The value of the attribute")
    String value;

    @Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Option(names = "--ttl", description = "Time to live in seconds. The attribute expires after this duration. If the TTL isn't set or a negative number it will use the fall back of 292 years", defaultValue = "-1")
    Long ttl;

    @Option(names = "--strategy", description = "Timeout strategy after TTL expires: ${COMPLETION-CANDIDATES}", defaultValue = "REMOVE")
    TimeOutStrategy strategy;

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
                """.formatted(entity, name, value, ttl < 0 ? Long.MAX_VALUE / 1_000_000_000L : ttl, strategy);

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

    // Hint: Hilfesmethoden buildKey(), parseArguments() werden über die abstrakte
    // Klasse geladen, da mehrfache Verwendung in Subcommands
    private Integer publishToStorage(AttributeStorage attributeStorage) {
        try {
            var args      = parseArguments(arguments);
            var key       = buildKey(entity, name, args);
            var duration  = ttl < 0 ? Duration.ofNanos(Long.MAX_VALUE) : Duration.ofSeconds(ttl);
            var persisted = new PersistedAttribute(Value.of(value), Instant.now(), duration, strategy,
                    Instant.now().plus(duration));

            attributeStorage.put(key, persisted).block();
            return 0;
        } catch (Exception e) {
            spec.commandLine().getErr().println("Failed to publish attribute: " + e.getMessage());
            return 1;
        }
    }
}
