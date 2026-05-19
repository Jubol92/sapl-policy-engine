package io.sapl.node.cli.commands;

import io.sapl.attributes.storage.AttributeStorage;
import picocli.CommandLine;
import java.util.List;

@CommandLine.Command(name = "delete", mixinStandardHelpOptions = true, description = "Removes an attribute from the attribute repository of a running SAPL node")

public class DeleteAttributeCommand extends BaseAttributeCommand {

    @CommandLine.Option(names = "--entity", required = true, description = "The subject or resource the attribute belongs to")
    String entity;

    @CommandLine.Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
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

    // Hint: Args werden in API komplett ignoriert. tbd
    private Integer deleteViaApi() {
        // Hint: sendet einen DELETE-Requests an den Endpoint der Push API
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

    // Hint: Hilfsmethoden buildKey(), parseArguments() werden über die abstrakte
    // Klasse geladen, da mehrfache Verwendung in Subcommands
    private Integer deleteFromStorage(AttributeStorage attributeStorage) {
        try {
            var args = parseArguments(arguments);
            var key  = buildKey(entity, name, args);

            attributeStorage.remove(key);
            return 0;
        } catch (Exception ex) {
            spec.commandLine().getErr().println("Failed to delete attribute: " + ex.getMessage());
            return 1;
        }
    }
}
