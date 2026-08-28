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

import io.micronaut.core.annotation.Experimental;
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
@Experimental
public record AsyncFunctionDef(
    String name,
    Object args,
    List<FunctionDef> decoratorList,
    Object returns,
    String typeComment,
    List<TypeVar> typeParams
) {
}
