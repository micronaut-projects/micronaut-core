package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import java.util.Objects;

/**
 * Represents a Python method/function as an {@link io.micronaut.inject.ast.Element}.
 * <p>
 * This class wraps a {@link FunctionDef} node of the Python AST, allowing integration with Java-based
 * modeling of Python code elements. It provides access to function metadata, such as the function's
 * name and underlying AST node, and standard implementations for equality, hashing, and string representation.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 *     FunctionDef def = new FunctionDef("my_func", ...);
 *     PythonMethodElement methodElement = new PythonMethodElement(def);
 *     System.out.println(methodElement.getName()); // Outputs: my_func
 * </pre>
 * </p>
 *
 * @see FunctionDef
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public final class PythonMethodElement extends AbstractPythonElement {
    /**
     * Constructs a new {@code PythonMethodElement} from the given {@code FunctionDef}.
     *
     * @param functionDef the function definition node; must not be {@code null}
     * @throws NullPointerException if {@code functionDef} is {@code null}
     */
    public PythonMethodElement(FunctionDef functionDef, ElementAnnotationMetadataFactory metadataFactory) {
        super(Objects.requireNonNull(functionDef, "FunctionDef cannot be null").name(), functionDef, metadataFactory);
    }

    /**
     * Returns the native {@link FunctionDef} object that backs this element.
     *
     * @return the underlying {@code FunctionDef} node
     */
    @Override
    public FunctionDef getNativeType() {
        return (FunctionDef) super.getNativeType();
    }

    /**
     * Returns a string representation of the Python function, including its name.
     *
     * @return a string in the format "Python Function: &lt;functionName&gt;"
     */
    @Override
    public String toString() {
        return "Python Function: " + getNativeType().name();
    }
}
