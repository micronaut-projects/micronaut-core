package io.micronaut.python.processing.visitor;

/**
 * An ArgumentDef represents a single function parameter definition.
 * <p>
 * This record captures the details of a Python function argument including its name,
 * type annotation, default value, and documentation.
 * </p>
 *
 * @param name The parameter name.
 * @param typeAnnotation The type annotation string (e.g., "int", "str", "List[str]").
 * @param defaultValue The default value as a GraalPy Value, or null if no default.
 * @param documentation The parameter documentation string.
 * @author Micronaut Team
 * @since 5.0.0
 */
public record ArgumentDef(
    String name,
    String typeAnnotation,
    Object defaultValue,
    String documentation
) {
    public ArgumentDef(String name, String typeAnnotation) {
        this(name, typeAnnotation, null, null);
    }

    /**
     * Creates an argument definition.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, String typeAnnotation) {
        return new ArgumentDef(name, typeAnnotation, null, null);
    }

    /**
     * Creates an argument definition with name only.
     *
     * @param name The parameter name
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name) {
        return new ArgumentDef(name, null, null, null);
    }

    /**
     * Creates an argument definition with name, type, and default value.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @param defaultValue The default value (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, String typeAnnotation, Object defaultValue) {
        return new ArgumentDef(name, typeAnnotation, defaultValue, null);
    }

    /**
     * Creates an argument definition with name, type, default value, and documentation.
     *
     * @param name The parameter name
     * @param typeAnnotation The type annotation (nullable)
     * @param defaultValue The default value (nullable)
     * @param documentation The parameter documentation (nullable)
     * @return A new ArgumentDef
     */
    public static ArgumentDef of(String name, String typeAnnotation, Object defaultValue, String documentation) {
        return new ArgumentDef(name, typeAnnotation, defaultValue, documentation);
    }
}
