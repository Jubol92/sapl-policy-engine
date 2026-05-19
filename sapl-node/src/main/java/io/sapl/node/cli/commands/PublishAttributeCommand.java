package io.sapl.node.cli.commands;

import io.sapl.api.model.Value;
import io.sapl.attributes.storage.AttributeStorage;
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

    @Option(names = "--ttl", description = "Time to live in seconds. Omit or use -1 for permanent attributes.", defaultValue = "-1")
    Long ttl;

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
                    "value": "%s",
                    "arguments": []
                }
                """.formatted(value);

        var uri     = storage.transport.url + "/api/attributes/" + entity + "/" + name;
        var request = webClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON);

        if (ttl >= 0) {
            request = request.header("ttl", String.valueOf(ttl));
        }

        var response = request.bodyValue(json).retrieve().toEntity(String.class).block();
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
            var     args      = parseArguments(arguments);
            var     key       = buildKey(entity, name, args);
            Instant expiresAt = ttl < 0 ? null : Instant.now().plus(Duration.ofSeconds(ttl));
            var     entry     = new AttributeStorage.StorageEntry(Value.of(value), expiresAt);
            attributeStorage.put(key, entry);
            return 0;
        } catch (Exception e) {
            spec.commandLine().getErr().println("Failed to publish attribute: " + e.getMessage());
            return 1;
        }
    }
}
