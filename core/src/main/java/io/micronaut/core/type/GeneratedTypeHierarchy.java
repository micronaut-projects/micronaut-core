/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.type;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The {@link TypeHierarchy} a generated record extends: a class with a single constructor, which the record calls with
 * the type, its variables, its direct super types and all its super types built as {@link Argument}s from constants,
 * so that a record is as small as that one call. It is public and not final because the records, generated into the
 * packages of the application, extend it: {@link TypeHierarchy} is sealed to it, so this class and its subclasses are
 * the only implementations.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public non-sealed class GeneratedTypeHierarchy implements TypeHierarchy {

    private final Class<?> type;
    private final List<Argument<?>> typeVariables;
    @Nullable
    private final Argument<?> superclass;
    private final List<Argument<?>> interfaces;
    private final List<Argument<?>> superTypes;

    /**
     * @param type          The type
     * @param typeVariables The type variables, or {@code null} for none
     * @param superclass    The generic superclass, or {@code null} for none
     * @param interfaces    The generic interfaces, or {@code null} for none
     * @param superTypes    All the super types, {@link Object} last, or {@code null} for {@link Object} alone
     */
    @UsedByGeneratedCode
    protected GeneratedTypeHierarchy(Class<?> type,
                                     Argument<?> @Nullable [] typeVariables,
                                     @Nullable Argument<?> superclass,
                                     Argument<?> @Nullable [] interfaces,
                                     Argument<?> @Nullable [] superTypes) {
        this.type = type;
        this.typeVariables = typeVariables == null ? List.of() : List.of(typeVariables);
        this.superclass = superclass;
        this.interfaces = interfaces == null ? List.of() : List.of(interfaces);
        this.superTypes = superTypes == null ? List.of(Argument.OBJECT_ARGUMENT) : List.of(superTypes);
    }

    @Override
    public final Class<?> getType() {
        return type;
    }

    @Override
    public final List<Argument<?>> getTypeVariables() {
        return typeVariables;
    }

    @Override
    public final Optional<Argument<?>> getGenericSuperclass() {
        return Optional.ofNullable(superclass);
    }

    @Override
    public final List<Argument<?>> getGenericInterfaces() {
        return interfaces;
    }

    @Override
    public final List<Argument<?>> getSuperTypes() {
        return superTypes;
    }

    @Override
    public String toString() {
        return "TypeHierarchy[" + getDeclaration().getTypeString(false) + " : "
            + superTypes.stream().map(superType -> superType.getTypeString(false)).toList() + ']';
    }
}
