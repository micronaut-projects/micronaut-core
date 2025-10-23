package io.micronaut.python.processing.visitor;

import java.util.List;

/**
 * A FunctionDef node represents a function definition.
 * <p>
 * FunctionDef(identifier name, arguments args, list[stmt] body, list[expr] decorator_list, expr | None returns, string | None type_comment, list[type_param] type_params)
 * </p>
 *
 * @param name The name of the function.
 * @param args The arguments.
 * @param decorators The decorators.
 * @param returns The return annotation.
 * @param typeComment The type comment.
 * @param typeParams The type parameters.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public record FunctionDef(
    String name,
    Object args,
    List<DecoratorDef> decorators,
    Object returns,
    String typeComment,
    List<Object> typeParams
) implements ElementDef {

    public FunctionDef(String name, Object args, Object returns) {
        this(name, args, List.of(), returns, "", List.of());
    }

    public FunctionDef(String name) {
        this(name, null, List.of(), null, "", List.of());
    }

    public FunctionDef(String name, List<DecoratorDef> decoratorList) {
        this(name, null, decoratorList, null, "", List.of());
    }
}
