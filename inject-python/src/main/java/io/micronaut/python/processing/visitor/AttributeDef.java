package io.micronaut.python.processing.visitor;

import java.util.List;
import java.util.Objects;

/**
 * An AttributeDef node represents a class attribute definition.
 * <p>
 * AttributeDef(identifier name, expr? annotation, expr? value, list[DecoratorDef] decorators, str? documentation, bool isStatic)
 * </p>
 *
 * @param name The name of the attribute.
 * @param annotation The type annotation.
 * @param value The default value.
 * @param decorators The decorators.
 * @param documentation The documentation string.
 * @param isStatic Whether the attribute is static (class-level).
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.Assign">Python AST Assign</a>
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.AnnAssign">Python AST AnnAssign</a>
 */
public record AttributeDef(
    String name,
    String annotation,
    Object value,
    List<DecoratorDef> decorators,
    String documentation,
    boolean isStatic
) implements ElementDef {

    public AttributeDef(String name) {
        this(name, null, null, List.of(), null, false);
    }

    public AttributeDef(String name, String annotation, Object value) {
        this(name, annotation, value, List.of(), null, false);
    }

    public AttributeDef {
        Objects.requireNonNull(name, "Attribute name cannot be null");
        if (decorators == null) {
            decorators = List.of();
        }
    }
}
