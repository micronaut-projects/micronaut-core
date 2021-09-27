/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Represents a generic placeholder in source code. A placeholder is a generic type that has not yet had the type bound yet – {@code List<T>} has a generic placeholder, whilst {@code List<String>} does not.
 * <p>
 * For compatibility, this a {@link io.micronaut.inject.ast.GenericPlaceholderElement} acts like its first upper bound when used as a {@link ClassElement}.
 *
 * @since 3.1.0
 * @author Jonas Konrad
 * @author graemerocher
 */
@Experimental
public interface GenericPlaceholderElement extends ClassElement {
    /**
     * Returns the bounds of this the generic placeholder empty. Always returns a non-empty list.
     *
     * @return The bounds declared for this type variable.
     */
    @NonNull
    List<? extends ClassElement> getBounds();

    /**
     * @return The name of the placeholder variable.
     */
    @NonNull
    String getVariableName();

    /**
     * @return The element declaring this variable, if it can be determined. Must be either a method or a class.
     */
    Optional<Element> getDeclaringElement();
}
