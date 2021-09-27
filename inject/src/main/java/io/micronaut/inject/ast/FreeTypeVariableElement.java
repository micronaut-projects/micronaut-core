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
 * Represents a <i>free</i> type variable. "Free" means that it is not bound to a type yet – {@code List<T>} has a free
 * type variable, {@code List<String>} does not.
 * <p>
 * For compatibility, this variable acts like its first upper bound when used as a {@link ClassElement}.
 */
@Experimental
public interface FreeTypeVariableElement extends ClassElement {
    /**
     * @return The bounds declared for this type variable.
     */
    @NonNull
    List<? extends ClassElement> getBounds();

    /**
     * @return The name of this variable.
     */
    @NonNull
    String getVariableName();

    /**
     * @return The element declaring this variable, if it can be determined. Must be either a method or a class.
     */
    @NonNull
    Optional<Element> getDeclaringElement();
}
