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
 * A TypeDef represents a reference to a type that may have type arguments.
 * Type arguments are recursive, allowing for nested generic types like dict[str, int].
 *
 * @param name The fully qualified name of the class.
 * @param typeArguments The type arguments if the type is generic (e.g., [TypeDef("str")] for MyBase[str], or [TypeDef("dict", [TypeDef("str"), TypeDef("int")])]).
 * @see ClassDef
 */
public record TypeRef(
    String name,
    List<TypeRef> typeArguments
) {

    public TypeRef {
        Objects.requireNonNull(name, "Type name cannot be null");
        if (typeArguments == null) {
            typeArguments = List.of();
        } else {
            typeArguments = List.copyOf(typeArguments);
        }
    }

    public TypeRef(String name) {
        this(name, List.of());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeRef that = (TypeRef) o;
        return Objects.equals(name, that.name) && Objects.equals(typeArguments, that.typeArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, typeArguments);
    }

    @Override
    public String toString() {
        if (typeArguments.isEmpty()) {
            return name;
        } else {
            return name + "[" + typeArguments.stream().map(TypeRef::toString).reduce((a, b) -> a + ", " + b).orElse("") + "]";
        }
    }
}
