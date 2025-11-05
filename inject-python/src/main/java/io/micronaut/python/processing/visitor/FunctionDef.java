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

import java.util.ArrayList;
import java.util.List;
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
 * @param returnType The parsed return type information.
 * @param typeComment The type comment.
 * @param typeParams The type parameters.
 * @param documentation The function documentation string.
 * @param isAbstract Whether the function is abstract (decorated with @abstractmethod).
 * @param declaringClass Declaring class, can be null if there is none
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.FunctionDef">Python AST FunctionDef</a>
 */
public record FunctionDef(
    String name,
    ArgumentsDef arguments,
    List<DecoratorDef> decorators,
    ReturnDef returnType,
    String typeComment,
    List<Object> typeParams,
    String documentation,
    boolean isAbstract,
    ClassDef declaringClass
) implements ElementDef {

    public static final String CONSTRUCTOR_NAME = "__init__";

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
    public FunctionDef(String name, ArgumentsDef arguments, List<DecoratorDef> decorators, ReturnDef returnType) {
        this(name, arguments, decorators, returnType, "", java.util.List.of(), null, false, null);
    }

    public FunctionDef(String name, ArgumentsDef arguments, ReturnDef returnType) {
        this(name, arguments, java.util.List.of(), returnType, "", java.util.List.of(), null, false, null);
    }

    public FunctionDef(String name) {
        this(name, ArgumentsDef.empty(), java.util.List.of(), ReturnDef.none(), "", java.util.List.of(), null, false, null);
    }

    public FunctionDef(String name, List<DecoratorDef> decoratorList) {
        this(name, ArgumentsDef.empty(), decoratorList, ReturnDef.none(), "", java.util.List.of(), null, false, null);
    }

    // Backward compatibility constructors
    public FunctionDef(String name, List<String> argumentNames, List<String> argumentTypes, List<DecoratorDef> decorators, String returnTypeAnnotation) {
        this(name, createArgumentsDef(argumentNames, argumentTypes), decorators,
             returnTypeAnnotation != null && !returnTypeAnnotation.isEmpty() ? ReturnDef.of(returnTypeAnnotation) : ReturnDef.none(),
             "", java.util.List.of(), null, false, null);
    }

    public FunctionDef(String name, List<String> argumentNames, List<String> argumentTypes, String returnTypeAnnotation) {
        this(name, createArgumentsDef(argumentNames, argumentTypes), java.util.List.of(),
             returnTypeAnnotation != null && !returnTypeAnnotation.isEmpty() ? ReturnDef.of(returnTypeAnnotation) : ReturnDef.none(),
             "", java.util.List.of(), null, false, null);
    }

    // Constructor for Python interop with return type decorators
    public FunctionDef(String name, ArgumentsDef arguments, List<DecoratorDef> decorators, String returnTypeAnnotation, List<DecoratorDef> returnTypeDecorators) {
        this(name, arguments, decorators,
             returnTypeAnnotation != null && !returnTypeAnnotation.isEmpty() ? ReturnDef.of(returnTypeAnnotation, returnTypeDecorators) : ReturnDef.none(),
             "", java.util.List.of(), null, false, null);
    }

    public FunctionDef(String name, ArgumentsDef arguments, List<DecoratorDef> decorators, ReturnDef returnType, String typeComment, List<Object> typeParams, String documentation, boolean isAbstract) {
        this(name, arguments, decorators, returnType, typeComment, typeParams, documentation, isAbstract, null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FunctionDef that = (FunctionDef) o;
        return Objects.equals(name, that.name) && Objects.equals(declaringClass, that.declaringClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, declaringClass);
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
        return returnType;
    }

    public FunctionDef withClassDef(ClassDef classDef) {
        return new FunctionDef(
            name,
            arguments.withDeclaringFunction(this),
            decorators,
            returnType,
            typeComment,
            typeParams,
            documentation,
            isAbstract,
            classDef
        );
    }
}
