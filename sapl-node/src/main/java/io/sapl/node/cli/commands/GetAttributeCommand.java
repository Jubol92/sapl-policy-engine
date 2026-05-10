package io.sapl.node.cli.commands;

import io.sapl.api.attributes.AttributeStorage;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import java.io.IOException;
import java.util.List;

@CommandLine.Command(name = "get", mixinStandardHelpOptions = true, description = "Gets an attribute from the attribute repository")
public class GetAttributeCommand extends BaseAttributeCommand {

    @Mixin
    FileMixin file;

    @CommandLine.Option(names = "--entity", required = true, description = "The subject or resource the attribute belongs to")
    String entity;

    @CommandLine.Option(names = "--name", description = "The attribute name e.g. role or user.role")
    String name;

    // Hint: Wichtig, wenn man mehrere Parameter mit übergeben kann ein Separator zu
    // haben
    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    // Hint: Eine Idee. Zeige nicht nur die Attribute an, sondern auch die Attribute
    // Keys. Es macht es manchmal einfacher
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

    // Hint: Alte anfängliche Implementierung für schnelle Tests - nicht ausgereift.
    // Wird tendenziell eher entfernt. Noch klären!
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

    // Hint: Hilfesmethoden buildKey(), parseArguments() werden über die abstrakte
    // Klasse geladen, da mehrfache Verwendung in Subcommands
    private Integer getFromStorage(AttributeStorage attributeStorage) {
        try {
            var args   = parseArguments(arguments);
            var key    = buildKey(entity, name, args);
            var result = attributeStorage.get(key).block();

            // Show the attribute key only if it's explicitly set as an option
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
