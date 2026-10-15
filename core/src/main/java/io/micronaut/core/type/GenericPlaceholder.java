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
     * recorded, answers the type it erases to. A placeholder whose type is an array stands for an array of the
     * variable, the {@code T[]} of a {@code T} bounded by {@code Payment}: its type is {@code Payment[]} and its
     * bounds are the variable's, {@code Payment}.</p>
     *
     * <p>A bound is written the way a type argument is, so a bound that is itself a type variable, or that names
     * one among its own type arguments, has a placeholder there: the bound of {@code T extends Comparable<T>} is
     * {@code Comparable} with a placeholder named {@code T} as its type argument.</p>
     *
     * @return The bounds, never empty
     * @since 5.2.0
     */
    @Experimental
    default List<Argument<?>> getBounds() {
        Class<?> type = getType();
        while (type.isArray()) {
            // the placeholder of an array of the variable: the variable erases to the component
            type = type.getComponentType();
        }
        return List.of(Argument.of(type, (String) null, getTypeParameters()));
    }

    /**
     * @return The variable name, never {@code null}.
     */
    default String getVariableName() {
        return Objects.requireNonNull(getName(), "Argument name cannot be null");
    }

    /**
     * Whether this placeholder stands for a type that was resolved in place of the type variable, rather than
     * for the variable itself.
     *
     * <p>A placeholder is usually a type variable left unresolved where the argument was built: the {@code T} of
     * {@code class Bean<T extends Payment>}, whose {@link #getType()} is the type the variable erases to. Some
     * arguments keep the shape of a placeholder for a type that took the variable's place:
     * {@link Argument#of(Class, io.micronaut.core.annotation.AnnotationMetadata, Class[])} builds one for each
     * class it is given, and a processor may compile the {@code Integer} that {@code class Sub extends
     * Base<Integer>} puts in place of {@code T} into a placeholder named {@code T}. Such a placeholder answers
     * {@code true} here: {@link #getType()} is the type it was resolved to, and {@link #getVariableName()} names
     * the variable it was resolved in place of. {@link #getBounds()} are that variable's bounds only where they were
     * recorded, as the processors do; a resolved placeholder built from a class records none, and answers the
     * resolved type. A consumer that wants the type reads {@link #getType()}, one that wants the declaration keeps
     * the variable.</p>
     *
     * <p>{@link #isTypeVariable()}, {@link #equals(Object)}, {@link #equalsType(Argument)} and
     * {@link #typeHashCode()} do not consider it. {@code false} is not proof that the variable is unresolved: a
     * placeholder compiled before this was recorded answers {@code false} whatever it holds, and so does one built
     * by hand with {@link Argument#ofTypeVariable(Class, String)}.</p>
     *
     * @return Whether the placeholder stands for a type resolved in place of its variable
     * @since 5.3.0
     */
    @Experimental
    default boolean isResolved() {
        return false;
    }

    @Override
    default boolean isTypeVariable() {
        return true;
    }
}
