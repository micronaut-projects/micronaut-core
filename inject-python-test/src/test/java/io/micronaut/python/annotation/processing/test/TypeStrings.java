package io.micronaut.python.annotation.processing.test;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.WildcardArgument;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prints arguments the way a declaration reads, for the assertions of the type hierarchy specs.
 */
public final class TypeStrings {

    private TypeStrings() {
    }

    public static List<String> strings(List<Argument<?>> arguments) {
        return arguments.stream().map(TypeStrings::string).toList();
    }

    public static String string(Argument<?> argument) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            if (!wildcard.getLowerBounds().isEmpty()) {
                return "? super " + bounds(wildcard.getLowerBounds());
            }
            if (wildcard.getUpperBounds().isEmpty() || (wildcard.getUpperBounds().size() == 1 && wildcard.getUpperBounds().get(0).getType() == Object.class)) {
                return "?";
            }
            return "? extends " + bounds(wildcard.getUpperBounds());
        }
        if (argument instanceof GenericPlaceholder<?> placeholder && !placeholder.isResolved()) {
            StringBuilder name = new StringBuilder(placeholder.getVariableName());
            for (Class<?> type = argument.getType(); type.isArray(); type = type.getComponentType()) {
                name.append("[]");
            }
            return name.toString();
        }
        String name = argument.getType().getSimpleName();
        if (!argument.hasTypeArguments()) {
            return name;
        }
        return name + "<" + java.util.Arrays.stream(argument.getTypeParameters()).map(TypeStrings::string).collect(Collectors.joining(", ")) + ">";
    }

    private static String bounds(List<Argument<?>> bounds) {
        return bounds.stream().map(TypeStrings::string).collect(Collectors.joining(" & "));
    }
}
