/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.ast;

import org.jetbrains.annotations.NotNull;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

/**
 * Represents a parameter to a method or constructor.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ParameterElement extends TypedElement {

    /**
     * @return The type of the parameter
     */
    @NotNull
    @Override
    @NonNull
    ClassElement getType();

    /**
     * Creates a parameter element for a simple type and name
     * @param type The type
     * @param name The name
     * @return The parameter element
     */
    static ParameterElement of(Class<?> type, String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return new ReflectParameterElement(ClassElement.of(type), name);
    }
}
