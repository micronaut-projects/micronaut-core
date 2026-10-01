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
package io.micronaut.core.type;

import io.micronaut.core.annotation.Experimental;

import java.util.List;
import java.util.Objects;

/**
 * Extends {@link Argument} to allow differentiating the
 * variable name from the argument name in cases where this is
 * required (parameters and fields for example).
 *
 * @param <T> The generic type
 * @since 3.2.0
 */
public interface GenericPlaceholder<T> extends Argument<T> {

    /**
     * The bounds declared for the type variable this placeholder stands for: the {@code Payment} and
     * {@code Refundable} of {@code <T extends Payment & Refundable>}.
     *
     * <p>The annotation processors compile a type variable to an argument of the type it erases to, so
     * {@link #getType()} stays the first bound, or the type the variable was resolved to; the bounds are kept
     * alongside, the way {@link java.lang.reflect.TypeVariable#getBounds()} reports them: a variable that
     * declares no bound is bounded by {@code Object}. They are not considered by {@link #equals(Object)},
     * {@link #equalsType(Argument)} or {@link #typeHashCode()}.</p>
     *
     * <p>A placeholder that carries no recorded bounds, one built by hand or compiled before the bounds were
     * recorded, answers the type it erases to.</p>
     *
     * @return The bounds, never empty
     * @since 5.2.0
     */
    @Experimental
    default List<Argument<?>> getBounds() {
        return List.of(Argument.of(getType(), (String) null, getTypeParameters()));
    }

    /**
     * @return The variable name, never {@code null}.
     */
    default String getVariableName() {
        return Objects.requireNonNull(getName(), "Argument name cannot be null");
    }

    @Override
    default boolean isTypeVariable() {
        return true;
    }
}
