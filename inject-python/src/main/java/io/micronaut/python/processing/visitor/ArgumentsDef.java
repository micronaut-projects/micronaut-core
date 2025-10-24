package io.micronaut.python.processing.visitor;

import java.util.List;

/**
 * An ArgumentsDef represents the complete argument specification for a function.
 * <p>
 * This record contains all parameters of a Python function including their names,
 * type annotations, and default values.
 * </p>
 *
 * @param arguments The list of function arguments.
 * @author Micronaut Team
 * @since 5.0.0
 */
public record ArgumentsDef(
    List<ArgumentDef> arguments
) {

    /**
     * Creates an ArgumentsDef with an empty argument list.
     *
     * @return A new ArgumentsDef with no arguments
     */
    public static ArgumentsDef empty() {
        return new ArgumentsDef(List.of());
    }

    /**
     * Creates an ArgumentsDef from a list of arguments.
     *
     * @param arguments The argument list
     * @return A new ArgumentsDef
     */
    public static ArgumentsDef of(List<ArgumentDef> arguments) {
        return new ArgumentsDef(arguments);
    }
}
