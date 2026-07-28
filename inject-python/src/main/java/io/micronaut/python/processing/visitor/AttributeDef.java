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
import java.util.Objects;

import org.graalvm.polyglot.Value;

/**
 * An AttributeDef node represents a class attribute definition.
 * <p>
 * AttributeDef(identifier name, expr? annotation, expr? value, list[DecoratorDef] decorators, str? documentation, bool isStatic, TypeRef? typeName, ClassDef declaringClass)
 * </p>
 *
 * @param name The name of the attribute.
 * @param annotation The full type annotation string.
 * @param typeName The extracted type name (e.g., TypeRef for "List[str]").
 * @param value The default value.
 * @param hasDefaultValue Whether the attribute declaration includes a default value, including an explicit {@code None}.
 * @param decorators The decorators.
 * @param documentation The documentation string.
 * @param isStatic Whether the attribute is static (class-level).
 * @param declaringClass The class that declares this attribute.
 * @param defaultFactoryName The dataclass default factory function name, when declared through {@code field(default_factory=...)}.
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.Assign">Python AST Assign</a>
 * @see <a href="https://docs.python.org/3/library/ast.html#ast.AnnAssign">Python AST AnnAssign</a>
 */
@Experimental
public record AttributeDef(
    String name,
    String annotation,
    TypeRef typeName,
    Value value,
    boolean hasDefaultValue,
    List<DecoratorDef> decorators,
    String documentation,
    boolean isStatic,
    ClassDef declaringClass,
    String defaultFactoryName
) implements ElementDef, MemberDef {

    public AttributeDef(String name) {
        this(name, null, null, null, false, List.of(), null, false, null, null);
    }

    public AttributeDef(String name, String annotation, Value value) {
        this(name, annotation, null, value, value != null, List.of(), null, false, null, null);
    }

    public AttributeDef(String name,
                        String annotation,
                        TypeRef typeName,
                        Value value,
                        List<DecoratorDef> decorators,
                        String documentation,
                        boolean isStatic,
                        ClassDef declaringClass) {
        this(name, annotation, typeName, value, value != null, decorators, documentation, isStatic, declaringClass, null);
    }

    public AttributeDef(String name,
                        String annotation,
                        TypeRef typeName,
                        Value value,
                        boolean hasDefaultValue,
                        List<DecoratorDef> decorators,
                        String documentation,
                        boolean isStatic,
                        ClassDef declaringClass) {
        this(name, annotation, typeName, value, hasDefaultValue, decorators, documentation, isStatic, declaringClass, null);
    }

    public AttributeDef {
        Objects.requireNonNull(name, "Attribute name cannot be null");
        if (decorators == null) {
            decorators = List.of();
        }
        // declaringClass can be null
    }

    /**
     * Creates a new AttributeDef with the given declaring class.
     *
     * @param declaringClass The class that declares this attribute
     * @return A new AttributeDef with the declaring class set
     */
    public AttributeDef withDeclaringClass(ClassDef declaringClass) {
        return new AttributeDef(name, annotation, typeName, value, hasDefaultValue, decorators, documentation, isStatic, declaringClass, defaultFactoryName);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AttributeDef that = (AttributeDef) o;
        return Objects.equals(name, that.name) && Objects.equals(declaringClass, that.declaringClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, declaringClass);
    }
}
