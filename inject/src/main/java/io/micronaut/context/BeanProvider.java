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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A provider contract.
 *
 * @param <T> The type
 * @author James Kleeh
 * @author graemerocher
 * @since 2.4.0
 */
public interface BeanProvider<T> extends Iterable<T> {

    /**
     * @return A fully-constructed and injected instance of T.
     */
    @NonNull
    T get();

    /**
     * @param qualifier The qualifier to use.
     * @return A fully-constructed and injected instance of T.
     * @since 3.0.0
     */
    @NonNull
    default T get(@Nullable Qualifier<T> qualifier) {
        return get();
    }

    @NonNull
    @Override
    default Iterator<T> iterator() {
        return Collections.singletonList(get()).iterator();
    }

    /**
     * <p>
     * When called, provides back a Stream of the beans available in this provider. If no beans are found, it returns an empty
     * stream.
     * </p>
     *
     * @since 3.0.0
     * @return a <code>Stream</code> representing the beans associated with this {@link BeanProvider} object
     */
    default Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * <p>Determines if more than one bean matches the specified type and qualifiers.</p>>
     *
     * @since 3.0.0
     * @return <code>true</code> if only one bean matches.
     */
    default boolean isUnique() {
        return true;
    }

    /**
     * <p>Determines if there is a bean that matches the required type and qualifiers.</p>
     *
     * @since 3.0.0
     * @return true if at least one bean matches.
     */
    default boolean isPresent() {
        return true;
    }

    /**
     * Is the bean resolvable using the {@link #get()} or {@link #ifPresent(Consumer)} methods.
     *
     * <p>A bean is said to be resolvable when it is both unique (see {@link #isUnique()}) and present (see {@link #isPresent()})</p>
     *
     * @return True if the bean is resolvable
     * @since 3.0.0
     */
    default boolean isResolvable() {
        return isUnique() && isPresent();
    }

    /**
     * Executes the given logic if the bean is present. Executes {@link #get()} to obtain the bean which may result in a {@link io.micronaut.context.exceptions.NonUniqueBeanException} if the bean is not unique.
     *
     * @param consumer the consumer
     * @since 3.0.0
     * @see #isPresent()
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException if the bean is not unique
     */
    default void ifPresent(@NonNull Consumer<T> consumer) {
        if (isPresent()) {
            Objects.requireNonNull(consumer, "Consumer cannot be null")
                    .accept(get());
        }
    }

    /**
     * Executes the given logic if the bean is resolvable. See {@link #isResolvable()}.
     *
     * @param consumer the consumer
     * @since 3.0.0
     * @see #isResolvable()
     */
    default void ifResolvable(@NonNull Consumer<T> consumer) {
        if (isResolvable()) {
            Objects.requireNonNull(consumer, "Consumer cannot be null")
                    .accept(get());
        }
    }

    /**
     * Create an argument for the given type to aid with bean provider lookup.
     *
     * @param type The type
     * @param <T1>  The generic type
     * @return 3.0.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static @NonNull <T1> Argument<BeanProvider<T1>> argumentOf(@NonNull Class<T1> type) {
        return (Argument) Argument.of(BeanProvider.class, Objects.requireNonNull(type, "Type cannot be null"));
    }

    /**
     * Allows selecting an alternative bean if the backing bean is not present.
     * @param alternative The alternative, can be {@code null}
     * @return The bean if present or else the supplied alternative
     * @since 3.0.2
     */
    default @Nullable T orElse(@Nullable T alternative) {
        if (isPresent()) {
            return get();
        } else {
            return alternative;
        }
    }
}
