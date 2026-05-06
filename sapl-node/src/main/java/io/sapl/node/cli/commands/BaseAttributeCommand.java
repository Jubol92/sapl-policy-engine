package io.sapl.node.cli.commands;

import io.sapl.api.model.Value;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

public abstract class BaseAttributeCommand implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

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
}
