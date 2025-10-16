package io.micronaut.python.processing.visitor;

import java.util.List;

/**
 * A ClassDef node represents a class definition.
 * <p>
 * ClassDef(identifier name, list[expr] bases, list[keyword] keywords, list[stmt] body, list[FunctionDef] decorator_list, list[TypeVar] type_params)
 * </p>
 *
 * @param name The name of the class.
 * @param bases The base classes.
 * @param decoratorList The decorators.
 * @param typeParams The type parameters.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.ClassDef">Python AST ClassDef</a>
 */
public record ClassDef(
    String name,
    List<Object> bases,
    List<FunctionDef> decoratorList,
    List<TypeVar> typeParams
) {

    public ClassDef(String name) {
        this(name, List.of(), List.of(), List.of());
    }

    public ClassDef(String name, List<FunctionDef> decoratorList) {
        this(name, List.of(), decoratorList, List.of());
    }
}
