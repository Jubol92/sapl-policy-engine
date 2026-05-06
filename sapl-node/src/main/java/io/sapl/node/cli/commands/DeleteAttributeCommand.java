package io.sapl.node.cli.commands;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.model.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import picocli.CommandLine;
import reactor.netty.http.HttpProtocol;
import java.util.List;

@CommandLine.Command(name = "delete", mixinStandardHelpOptions = true, description = "Removes an attribute from the attribute repository of a running SAPL node")

public class DeleteAttributeCommand extends BaseAttributeCommand {
    private static final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.H2C)))
            .build();

    @CommandLine.Mixin
    StorageTransportMixin storage;

    @CommandLine.Option(names = "--entity", required = true)
    String entity;

    @CommandLine.Option(names = "--name", required = true)
    String name;

    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Override
    public Integer call() throws Exception {
        if (storage.transport.url != null) {
            return deleteViaApi();
        }
        return deleteViaProvider();
    }

    private Integer deleteViaApi() {
        var response = webClient.delete().uri(storage.transport.url + "/api/attributes/entity/" + entity + "/" + name)
                .retrieve().toEntity(String.class).block();

        int status = response != null ? response.getStatusCode().value() : 0;
        spec.commandLine().getOut().println("Status: " + status);
        spec.commandLine().getOut().println("Response: " + (response != null ? response.getBody() : ""));

        return status == 200 ? 0 : 1;
    }

    private Integer deleteViaProvider() {
        try {
            return deleteFromStorage(storage.createStorage());
        } catch (IllegalArgumentException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return 1;
        }
    }

    private Integer deleteFromStorage(AttributeStorage attributeStorage) {
        try {
            var args = arguments.stream().filter(s -> !s.isEmpty()).map(this::parseArgument).toList();
            var key  = new AttributeKey(Value.of(entity), name, args);

            attributeStorage.remove(key).block();
            return 0;
        } catch (Exception ex) {
            spec.commandLine().getErr().println("Failed to delete attribute: " + ex.getMessage());
            return 1;
        }
    }
}
