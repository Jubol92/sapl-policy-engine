package io.sapl.node.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "attributes", mixinStandardHelpOptions = true, header = "Publish, get or remove attributes from the repository", description = {
        " Modifies, publishes, deletes or gets attribute from a given attribute storage." }, subcommands = {
                PublishAttributeCommand.class, DeleteAttributeCommand.class, GetAttributeCommand.class })

// Hint: Basis Command ohne eigene Optionen. Es dient nur zur Eingliederung der Subcommand damit es die Aufrufe
// attributes publish, attributes get und attribute delete gibt
// @formatter:on
@SuppressWarnings("unused")
public class AttributesCommand implements Callable<Integer> {
    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        return 0;
    }
}
