package io.micronaut.python.processing.visitor;

import java.util.List;

/**
 * An AsyncFunctionDef node represents an asynchronous function definition.
 * <p>
 * AsyncFunctionDef(identifier name, arguments args, list[stmt] body, list[expr] decorator_list, expr | None returns, string | None type_comment, list[type_param] type_params)
 * </p>
 *
 * @param name The name of the async function.
 * @param args The arguments.
 * @param decoratorList The decorators.
 * @param returns The return annotation.
 * @param typeComment The type comment.
 * @param typeParams The type parameters.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.AsyncFunctionDef">Python AST AsyncFunctionDef</a>
 */
public record AsyncFunctionDef(
    String name,
    Object args,
    List<FunctionDef> decoratorList,
    Object returns,
    String typeComment,
    List<TypeVar> typeParams
) {
}
