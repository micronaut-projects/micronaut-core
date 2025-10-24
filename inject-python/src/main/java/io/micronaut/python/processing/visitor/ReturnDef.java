package io.micronaut.python.processing.visitor;

/**
 * A ReturnDef represents the return type annotation of a function.
 * <p>
 * This record captures the return type annotation string from a Python function's
 * type hint (e.g., "-> int", "-> str", "-> List[str]").
 * </p>
 *
 * @param typeAnnotation The return type annotation string, or null if no return type is specified.
 * @author Micronaut Team
 * @since 5.0.0
 */
public record ReturnDef(
    String typeAnnotation
) {

    /**
     * Creates a ReturnDef with no return type annotation.
     *
     * @return A new ReturnDef with null type annotation
     */
    public static ReturnDef none() {
        return new ReturnDef(null);
    }

    /**
     * Creates a ReturnDef with the specified type annotation.
     *
     * @param typeAnnotation The return type annotation
     * @return A new ReturnDef
     */
    public static ReturnDef of(String typeAnnotation) {
        return new ReturnDef(typeAnnotation);
    }
}
