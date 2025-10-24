package io.micronaut.python.processing.visitor;

import java.util.List;
import java.util.ArrayList;

/**
 * A FunctionDef node represents a function definition.
 * <p>
 * FunctionDef(identifier name, arguments args, list[stmt] body, list[expr] decorator_list, expr | None returns, string | None type_comment, list[type_param] type_params)
 * </p>
 *
 * @param name The name of the function.
 * @param argumentNames The raw argument names.
 * @param argumentTypes The raw argument type annotations.
 * @param decorators The decorators.
 * @param returnTypeAnnotation The raw return type annotation.
 * @param typeComment The type comment.
 * @param typeParams The type parameters.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public record FunctionDef(
    String name,
    List<String> argumentNames,
    List<String> argumentTypes,
    List<DecoratorDef> decorators,
    String returnTypeAnnotation,
    String typeComment,
    List<Object> typeParams
) implements ElementDef {

    // Simplified constructors for easier Python interop
    public FunctionDef(String name, List<String> argumentNames, List<String> argumentTypes, List<DecoratorDef> decorators, String returnTypeAnnotation) {
        this(name, argumentNames, argumentTypes, decorators, returnTypeAnnotation, "", List.of());
    }

    public FunctionDef(String name, List<String> argumentNames, List<String> argumentTypes, String returnTypeAnnotation) {
        this(name, argumentNames, argumentTypes, List.of(), returnTypeAnnotation, "", List.of());
    }

    public FunctionDef(String name) {
        this(name, List.of(), List.of(), List.of(), "", "", List.of());
    }

    public FunctionDef(String name, List<DecoratorDef> decoratorList) {
        this(name, List.of(), List.of(), decoratorList, "", "", List.of());
    }

    /**
     * Get the parsed arguments as ArgumentsDef.
     */
    public ArgumentsDef arguments() {
        List<ArgumentDef> args = new ArrayList<>();
        for (int i = 0; i < argumentNames.size(); i++) {
            String argName = argumentNames.get(i);
            String argType = i < argumentTypes.size() ? argumentTypes.get(i) : "";
            args.add(ArgumentDef.of(argName, argType));
        }
        return ArgumentsDef.of(args);
    }

    /**
     * Get the parsed return type as ReturnDef.
     */
    public ReturnDef returnType() {
        return returnTypeAnnotation != null && !returnTypeAnnotation.isEmpty()
            ? ReturnDef.of(returnTypeAnnotation)
            : ReturnDef.none();
    }
}
