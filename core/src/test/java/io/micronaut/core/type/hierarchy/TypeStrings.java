package io.micronaut.core.type.hierarchy;

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
            if (wildcard.getUpperBounds().isEmpty() || (wildcard.getUpperBounds().size() == 1 && isPlainObject(wildcard.getUpperBounds().get(0)))) {
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
        // an array of a parameterized type keeps the parameters of its component: List<String>[]
        StringBuilder dimensions = new StringBuilder();
        Class<?> component = argument.getType();
        while (component.isArray()) {
            dimensions.append("[]");
            component = component.getComponentType();
        }
        String name = component.getSimpleName();
        if (!argument.hasTypeArguments()) {
            return name + dimensions;
        }
        return name + "<" + java.util.Arrays.stream(argument.getTypeParameters()).map(TypeStrings::string).collect(Collectors.joining(", ")) + ">" + dimensions;
    }

    /** Whether a bound is the implicit Object of an unbounded wildcard, rather than a variable that erases to it. */
    private static boolean isPlainObject(Argument<?> bound) {
        return bound.getType() == Object.class && !(bound instanceof GenericPlaceholder<?> placeholder && !placeholder.isResolved());
    }

    private static String bounds(List<Argument<?>> bounds) {
        return bounds.stream().map(TypeStrings::string).collect(Collectors.joining(" & "));
    }
}
