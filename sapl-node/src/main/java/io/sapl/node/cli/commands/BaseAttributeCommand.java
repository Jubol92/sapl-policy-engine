package io.sapl.node.cli.commands;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.model.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import reactor.netty.http.HttpProtocol;

import java.util.List;
import java.util.concurrent.Callable;

public abstract class BaseAttributeCommand implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

    protected static final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.H2C)))
            .build();

    // Hint: Storageimplementierung muss in jeder Subklasse vorhanden sein, da
    // derzeit der einzige wirkliche Weg
    @CommandLine.Mixin
    protected StorageTransportMixin storage;

    // Hint: Methode wird von Subcommands oft gebraucht. Damit Änderungen nur an
    // einer Stelle erfolgen hier implementiert.
    protected Value parseArgument(String argument) {
        if (argument.equalsIgnoreCase("true"))
            return Value.of(true);
        else if (argument.equalsIgnoreCase("false"))
            return Value.of(false);
        else if (argument.equalsIgnoreCase("null"))
            return Value.NULL;
        else if (argument.startsWith("\"") && argument.endsWith("\""))
            return Value.of(argument.substring(1, argument.length() - 1));
        else {
            try {
                return Value.of(Long.parseLong(argument));
            } catch (NumberFormatException ignore) {
            }

            try {
                return Value.of(Double.parseDouble(argument));
            } catch (NumberFormatException ignored) {
            }

            throw new IllegalArgumentException("Argument could not be parsed " + argument);
        }
    }

    // Hint: parsed die Argument, welche an Picocli in der form arg1,arg2,...,argN
    // übergeben werden
    // als List<Value> damit diese im Storage verwendbar sind
    protected List<Value> parseArguments(List<String> arguments) {
        return arguments.stream().filter(s -> !s.isEmpty()).map(this::parseArgument).toList();
    }

    // Hint: baut den Attributekey, welcher als primary key für die Storages
    // gebraucht wird
    // entity + name + args --> Eindeutig
    // zu klären: args können weggelassen werden und es können alle keys die matchen
    // ausgegeben werden?
    protected AttributeKey buildKey(String entity, String name, List<Value> args) {
        return new AttributeKey(Value.of(entity), name, args);
    }
}
