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

import io.micronaut.core.annotation.Experimental;

import java.util.List;

/**
 * An {@link Argument} that stands for a wildcard type argument, such as the {@code ?} of {@code Foo<?>}, the
 * {@code ? extends Number} of {@code List<? extends Number>} or the {@code ? super Book} of
 * {@code Consumer<? super Book>}.
 *
 * <p>The annotation processors compile a wildcard type argument to an argument of the type it is bounded by, so
 * {@link #getType()} is {@code Number} for {@code ? extends Number}, {@code Book} for {@code ? super Book} and
 * {@code Object}, or the declared bound of the type parameter the wildcard stands for, for {@code ?}. The bounds
 * are kept alongside, the way {@link java.lang.reflect.WildcardType} reports them: {@link #getUpperBounds()}
 * holds {@code Object} for a wildcard without an explicit upper bound, and {@link #getLowerBounds()} is empty
 * unless the wildcard declares {@code super}.</p>
 *
 * <p>For compatibility with what the processors have always emitted, a wildcard argument also reports
 * {@link #isTypeVariable()} as {@code true} and may implement {@link GenericPlaceholder}, named after the type
 * parameter it stands for; a consumer that needs to tell a wildcard from a type variable should test for this
 * interface first. {@link #equalsType(Argument)} and {@link #typeHashCode()} do not consider the bounds.</p>
 *
 * @param <T> The type the wildcard is bounded by
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public interface WildcardArgument<T> extends Argument<T> {

    /**
     * The upper bounds of the wildcard. A wildcard without an explicit upper bound, {@code ?} or
     * {@code ? super X}, is bounded above by {@code Object}.
     *
     * @return The upper bounds, never empty
     */
    List<Argument<?>> getUpperBounds();

    /**
     * The lower bounds of the wildcard: the {@code X} of {@code ? super X}.
     *
     * @return The lower bounds, empty unless the wildcard declares a lower bound
     */
    List<Argument<?>> getLowerBounds();

    /**
     * @return Whether the wildcard declares a lower bound ({@code ? super X})
     */
    default boolean hasLowerBound() {
        return !getLowerBounds().isEmpty();
    }

    @Override
    default boolean isTypeVariable() {
        return true;
    }
}
