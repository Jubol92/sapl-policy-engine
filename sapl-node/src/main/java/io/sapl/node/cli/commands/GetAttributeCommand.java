package io.sapl.node.cli.commands;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.model.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import reactor.netty.http.HttpProtocol;

import java.io.IOException;
import java.util.List;

@CommandLine.Command(name = "get", mixinStandardHelpOptions = true, description = "Gets an attribute from the attribute repository")
public class GetAttributeCommand extends BaseAttributeCommand {

    private static final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.H2C)))
            .build();

    @Mixin
    StorageTransportMixin storage;

    @Mixin
    FileMixin file;

    @CommandLine.Option(names = "--entity", required = true)
    String entity;

    @CommandLine.Option(names = "--name")
    String name;

    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @CommandLine.Option(names = "--with-key", description = "Displays the stored key along with the value.")
    Boolean showKey;

    @Override
    public Integer call() throws Exception {
        return file.run(() -> {
            if (storage.transport.url != null) {
                return getViaApi();
            }
            return getViaProvider();
        });
    }

    private Integer getViaApi() throws IOException {
        var response = webClient.get().uri(storage.transport.url + "/api/attributes/entity/" + entity).retrieve()
                .toEntity(String.class).block();

        print(response != null ? response.getBody() : "");
        return response != null && response.getStatusCode().value() == 200 ? 0 : 1;
    }

    private Integer getViaProvider() {
        try {
            return getFromStorage(storage.createStorage());
        } catch (IllegalArgumentException e) {
            spec.commandLine().getErr().println(e.getMessage());
            return 1;
        }
    }

    private Integer getFromStorage(AttributeStorage attributeStorage) {
        try {
            var args   = arguments.stream().filter(s -> !s.isEmpty()).map(this::parseArgument).toList();
            var key    = new AttributeKey(Value.of(entity), name, args);
            var result = attributeStorage.get(key).block();

            if (!Boolean.TRUE.equals(showKey)) {
                print(result != null ? result.toString() : "Not found.");
            } else {
                print(result != null ? key + " " + result : "Not found.");
            }
            return 0;
        } catch (Exception ex) {
            spec.commandLine().getErr().println("Failed to get attribute: " + ex.getMessage());
            return 1;
        }
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
