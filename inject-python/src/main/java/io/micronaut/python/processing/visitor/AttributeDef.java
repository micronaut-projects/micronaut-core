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

import org.graalvm.polyglot.Value;

/**
 * An AttributeDef node represents a class attribute definition.
 * <p>
 * AttributeDef(identifier name, expr? annotation, expr? value, list[DecoratorDef] decorators, str? documentation, bool isStatic, str? typeName)
 * </p>
 *
 * @param name The name of the attribute.
 * @param annotation The full type annotation string.
 * @param typeName The extracted type name (e.g., "float" from "Annotated[float, Gt(0)]").
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
    String typeName,
    Value value,
    List<DecoratorDef> decorators,
    String documentation,
    boolean isStatic
) implements ElementDef {

    public AttributeDef(String name) {
        this(name, null, null, null, List.of(), null, false);
    }

    public AttributeDef(String name, String annotation, Value value) {
        this(name, annotation, annotation, value, List.of(), null, false);
    }

    public AttributeDef {
        Objects.requireNonNull(name, "Attribute name cannot be null");
        if (decorators == null) {
            decorators = List.of();
        }
        // declaringClassName can be null
    }
}
