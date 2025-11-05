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
import java.util.Objects;

/**
 * An ArgumentsDef represents the complete argument specification for a function.
 * <p>
 * This record contains all parameters of a Python function including their names,
 * type annotations, and default values.
 * </p>
 *
 * @param arguments The list of function arguments.
 * @param declaringFunction The function that declares these arguments.
 * @author Micronaut Team
 * @since 5.0.0
 */
public record ArgumentsDef(
    List<ArgumentDef> arguments,
    FunctionDef declaringFunction
) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArgumentsDef that = (ArgumentsDef) o;
        return Objects.equals(arguments, that.arguments) && Objects.equals(declaringFunction, that.declaringFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(arguments, declaringFunction);
    }

    /**
     * Creates an ArgumentsDef with an empty argument list.
     *
     * @return A new ArgumentsDef with no arguments
     */
    public static ArgumentsDef empty() {
        return new ArgumentsDef(List.of(), null);
    }

    /**
     * Creates an ArgumentsDef from a list of arguments.
     *
     * @param arguments The argument list
     * @return A new ArgumentsDef
     */
    public static ArgumentsDef of(List<ArgumentDef> arguments) {
        return new ArgumentsDef(arguments, null);
    }

    /**
     * Creates an ArgumentsDef from a list of arguments with a declaring function.
     *
     * @param arguments The argument list
     * @param declaringFunction The function that declares these arguments
     * @return A new ArgumentsDef
     */
    public static ArgumentsDef of(List<ArgumentDef> arguments, FunctionDef declaringFunction) {
        return new ArgumentsDef(arguments, declaringFunction);
    }

    /**
     * Creates an ArgumentsDef from a list of arguments (backward compatibility).
     *
     * @param arguments The argument list
     * @return A new ArgumentsDef
     * @deprecated Use {@link #of(List)} instead
     */
    @Deprecated
    public static ArgumentsDef of(Object... arguments) {
        if (arguments.length == 1 && arguments[0] instanceof List) {
            return of((List<ArgumentDef>) arguments[0]);
        }
        throw new IllegalArgumentException("Invalid arguments for ArgumentsDef.of()");
    }

    /**
     * Creates a new ArgumentsDef with the given declaring function.
     *
     * @param declaringFunction The function that declares these arguments
     * @return A new ArgumentsDef with the declaring function set and propagated to arguments
     */
    public ArgumentsDef withDeclaringFunction(FunctionDef declaringFunction) {
        // Propagate the declaring function to each ArgumentDef
        List<ArgumentDef> updatedArguments = arguments.stream()
            .map(arg -> arg.withDeclaringFunction(declaringFunction))
            .toList();
        return new ArgumentsDef(updatedArguments, declaringFunction);
    }
}
