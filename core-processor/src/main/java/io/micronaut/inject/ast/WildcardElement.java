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

/**
 * Represents a wildcard, for example {@code List<?>}. For compatibility, this wildcard acts like its first upper bound when used as a
 * {@link ClassElement}.
 *
 * @since 3.1.0
 * @author Jonas Konrad
 */
@Experimental
public interface WildcardElement extends GenericElement {

    /**
     * @return The upper bounds of this wildcard. Never empty. To match this wildcard, a type must be assignable to all
     * upper bounds (must extend all upper bounds).
     */
    @NonNull
    List<? extends ClassElement> getUpperBounds();

    /**
     * @return The lower bounds of this wildcard. May be empty. To match this wildcard, a type must be assignable from
     * all lower bounds (must be a supertype of all lower bounds).
     */
    @NonNull
    List<? extends ClassElement> getLowerBounds();

    /**
     * Is bounded wildcard - not "{@code < ? >}".
     * @return true if the wildcard is bounded, false otherwise
     * @since 4.0.0
     */
    default boolean isBounded() {
        return !getName().equals("java.lang.Object");
    }

    /**
     * Find the most upper type.
     * @param bounds1 The bounds 1
     * @param bounds2 The bounds 2
     * @param <T> The class element type
     * @return the most upper type
     */
    @NonNull
    static <T extends ClassElement> T findUpperType(@NonNull List<T> bounds1, @NonNull List<T> bounds2) {
        T upper = null;
        for (T lowerBound : bounds2) {
            if (upper == null || lowerBound.isAssignable(upper)) {
                upper = lowerBound;
            }
        }
        for (T upperBound : bounds1) {
            if (upper == null || upperBound.isAssignable(upper)) {
                upper = upperBound;
            }
        }
        return upper;
    }
}
