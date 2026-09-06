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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;
import java.util.List;
import java.util.Objects;

/**
 * A TypeDef represents a reference to a type that may have type arguments.
 * Type arguments are recursive, allowing for nested generic types like dict[str, int].
 *
 * @param name The fully qualified name of the class.
 * @param typeArguments The type arguments if the type is generic (e.g., [TypeDef("str")] for MyBase[str], or [TypeDef("dict", [TypeDef("str"), TypeDef("int")])]).
 * @param typeUseDecorators The decorators applied through typing.Annotated when this type is used.
 * @see ClassDef
 */
@Experimental
public record TypeRef(
    String name,
    List<TypeRef> typeArguments,
    List<DecoratorDef> typeUseDecorators
) {

    /**
     * The name of a union type; its members are the {@link #typeArguments()}.
     */
    public static final String UNION = "|";

    /**
     * The name of Python's {@code None} type.
     */
    public static final String NONE = "None";

    public TypeRef {
        Objects.requireNonNull(name, "Type name cannot be null");
        if (typeArguments == null) {
            typeArguments = List.of();
        } else {
            typeArguments = List.copyOf(typeArguments);
        }
        if (typeUseDecorators == null) {
            typeUseDecorators = List.of();
        } else {
            typeUseDecorators = List.copyOf(typeUseDecorators);
        }
    }

    public TypeRef(String name, List<TypeRef> typeArguments) {
        this(name, typeArguments, List.of());
    }

    public TypeRef(String name) {
        this(name, List.of(), List.of());
    }

    /**
     * @param members The union members
     * @return A union type reference
     */
    public static TypeRef unionOf(List<TypeRef> members) {
        return new TypeRef(UNION, members, List.of());
    }

    /**
     * @return Whether this is a union type such as {@code str | None}
     */
    public boolean isUnion() {
        return UNION.equals(name);
    }

    /**
     * @return Whether this is {@code None}
     */
    public boolean isNone() {
        return NONE.equals(name);
    }

    /**
     * @return Whether this is a union with {@code None} as one of its members
     */
    public boolean isNullableUnion() {
        return isUnion() && typeArguments.stream().anyMatch(TypeRef::isNone);
    }

    /**
     * @return The union members other than {@code None}; the type itself when it is not a union
     */
    public List<TypeRef> nonNoneMembers() {
        if (!isUnion()) {
            return List.of(this);
        }
        return typeArguments.stream().filter(member -> !member.isNone()).toList();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeRef that = (TypeRef) o;
        return Objects.equals(name, that.name)
            && Objects.equals(typeArguments, that.typeArguments)
            && Objects.equals(typeUseDecorators, that.typeUseDecorators);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, typeArguments, typeUseDecorators);
    }

    @Override
    public String toString() {
        if (isUnion()) {
            return typeArguments.stream().map(TypeRef::toString).collect(java.util.stream.Collectors.joining(" | "));
        }
        if (typeArguments.isEmpty()) {
            return name;
        } else {
            return name + "[" + typeArguments.stream().map(TypeRef::toString).reduce((a, b) -> a + ", " + b).orElse("") + "]";
        }
    }
}
