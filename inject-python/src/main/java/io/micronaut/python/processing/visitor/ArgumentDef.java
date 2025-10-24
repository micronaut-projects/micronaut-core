package io.micronaut.python.processing.visitor;

/**
 * An ArgumentDef represents a single function parameter definition.
 * <p>
 * This record captures the details of a Python function argument including its name
 * and type annotation.
 * </p>
 *
 * @param name The parameter name.
 * @param typeAnnotation The type annotation string (e.g., "int", "str", "List[str]").
 * @author Micronaut Team
 * @since 5.0.0
 */
public record ArgumentDef(
    String name,
    String typeAnnotation
) {

    /**
     * Creates an argument definition.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, String typeAnnotation) {
        return new ArgumentDef(name, typeAnnotation);
    }

    /**
     * Creates an argument definition with name only.
     *
     * @param name The parameter name
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name) {
        return new ArgumentDef(name, null);
    }
}
