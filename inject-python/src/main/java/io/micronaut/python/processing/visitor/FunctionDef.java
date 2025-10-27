/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.processing.visitor;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A FunctionDef node represents a function definition.
 * <p>
 * FunctionDef(identifier name, arguments args, list[stmt] body, list[expr] decorator_list, expr | None returns, string | None type_comment, list[type_param] type_params)
 * </p>
 *
 * @param name The name of the function.
 * @param arguments The function arguments.
 * @param decorators The decorators.
 * @param returnTypeAnnotation The raw return type annotation.
 * @param typeComment The type comment.
 * @param typeParams The type parameters.
 * @param documentation The function documentation string.
 * @param isAbstract Whether the function is abstract (decorated with @abstractmethod).
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public record FunctionDef(
    String name,
    ArgumentsDef arguments,
    List<DecoratorDef> decorators,
    String returnTypeAnnotation,
    String typeComment,
    List<Object> typeParams,
    String documentation,
    boolean isAbstract
) implements ElementDef {

    public FunctionDef {
        Objects.requireNonNull(name, "Function name cannot be null");
        if (arguments == null) {
            arguments = ArgumentsDef.empty();
        }
        if (decorators == null) {
            decorators = List.of();
        }
        if (typeParams == null) {
            typeParams = List.of();
        }
        // declaringClassName can be null
    }

    // Simplified constructors for easier Python interop
    public FunctionDef(String name, ArgumentsDef arguments, List<DecoratorDef> decorators, String returnTypeAnnotation) {
        this(name, arguments, decorators, returnTypeAnnotation, "", java.util.List.of(), null, false);
    }

    public FunctionDef(String name, ArgumentsDef arguments, String returnTypeAnnotation) {
        this(name, arguments, java.util.List.of(), returnTypeAnnotation, "", java.util.List.of(), null, false);
    }

    public FunctionDef(String name) {
        this(name, ArgumentsDef.empty(), java.util.List.of(), "", "", java.util.List.of(), null, false);
    }

    public FunctionDef(String name, List<DecoratorDef> decoratorList) {
        this(name, ArgumentsDef.empty(), decoratorList, "", "", java.util.List.of(), null, false);
    }

    // Backward compatibility constructors
    public FunctionDef(String name, List<String> argumentNames, List<String> argumentTypes, List<DecoratorDef> decorators, String returnTypeAnnotation) {
        this(name, createArgumentsDef(argumentNames, argumentTypes), decorators, returnTypeAnnotation, "", java.util.List.of(), null, false);
    }

    public FunctionDef(String name, List<String> argumentNames, List<String> argumentTypes, String returnTypeAnnotation) {
        this(name, createArgumentsDef(argumentNames, argumentTypes), java.util.List.of(), returnTypeAnnotation, "", java.util.List.of(), null, false);
    }

    private static ArgumentsDef createArgumentsDef(List<String> argumentNames, List<String> argumentTypes) {
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
