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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Records, in the annotation metadata of an {@link io.micronaut.core.type.Argument} that stands for a type
 * argument, that the type argument was declared as a wildcard.
 *
 * <p>An argument has no wildcard representation of its own: the annotation processors compile a wildcard type
 * argument to an argument of the type it is bounded by, so that {@code List<? extends Number>} carries an
 * argument of type {@code Number}, {@code Consumer<? super Book>} one of type {@code Book} and {@code Foo<?>} one
 * of type {@code Object}, indistinguishable from {@code List<Number>}, {@code Consumer<Book>} and
 * {@code Foo<Object>}. This annotation is added to that argument's metadata by the processors, never written in
 * source, and tells the difference: the argument is present whenever the type argument was a wildcard, and
 * {@link #bound()} says whether the argument's type is the wildcard's explicit upper bound, its explicit lower
 * bound, or the implicit bound of an unbounded wildcard.</p>
 *
 * <p>A consumer that needs the wildcard back reads it from the argument's metadata:</p>
 *
 * <pre>{@code
 * Argument<?> typeArgument = listArgument.getTypeParameters()[0];
 * Wildcard.Bound bound = typeArgument.getAnnotationMetadata()
 *     .enumValue(Wildcard.class, "bound", Wildcard.Bound.class)
 *     .orElse(null); // null: not a wildcard
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Documented
@Retention(RUNTIME)
@Target({})
@Experimental
public @interface Wildcard {

    /**
     * @return How the argument's type relates to the wildcard
     */
    Bound bound() default Bound.NONE;

    /**
     * The bound of a wildcard that an argument's type stands for.
     *
     * @since 5.2.0
     */
    enum Bound {
        /**
         * The wildcard is unbounded ({@code ?}); the argument's type is the implicit bound.
         */
        NONE,
        /**
         * The wildcard declares an upper bound ({@code ? extends T}); the argument's type is that bound.
         */
        UPPER,
        /**
         * The wildcard declares a lower bound ({@code ? super T}); the argument's type is that bound.
         */
        LOWER
    }
}
