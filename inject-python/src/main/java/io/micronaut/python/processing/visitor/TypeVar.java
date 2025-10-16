package io.micronaut.python.processing.visitor;

import java.util.List;

/**
 * A TypeVar node represents a type variable for generic types.
 * <p>
 * TypeVar(identifier name, expr | None bound, list[expr] constraints)
 * </p>
 *
 * @param name The name of the type variable.
 * @param bound The bound of the type variable.
 * @param constraints The constraints of the type variable.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.TypeVar">Python AST TypeVar</a>
 */
public record TypeVar(
    String name,
    Object bound,
    List<Object> constraints
) {
}
