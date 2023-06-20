/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.propagation;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The propagation across different threads using the context which is immutable and can be extended/reduced by different elements.
 * Each element can be a simple data structure carrying its state across the threads, or it can implement {@link ThreadPropagatedContextElement}
 * for use-cases when thread-local values needs to be updated.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public interface PropagatedContext {

    /**
     * Returns an empty context.
     *
     * @return the empty context
     */
    @NonNull
    static PropagatedContext empty() {
        return PropagatedContextImpl.EMPTY;
    }

    /**
     * Returns the current context or an empty one.
     *
     * @return the current context or an empty one
     */
    @NonNull
    static PropagatedContext getOrEmpty() {
        return PropagatedContextImpl.getOrEmpty();
    }

    /**
     * Returns the current context or throws an exception otherwise.
     *
     * @return the current context
     */
    @NonNull
    static PropagatedContext get() {
        return PropagatedContextImpl.get();
    }

    /**
     * Returns an optional context.
     *
     * @return the current optional context
     */
    @NonNull
    static Optional<PropagatedContext> find() {
        return PropagatedContextImpl.find();
    }

    /**
     * Wrap runnable for this context to be propagated in.
     *
     * @param runnable The runnable
     * @return new runnable or existing if the context is missing
     */
    @NonNull
    static Runnable wrapCurrent(@NonNull Runnable runnable) {
        return PropagatedContext.find().map(ctx -> ctx.wrap(runnable)).orElse(runnable);
    }

    /**
     * Wrap callable for this context to be propagated in.
     *
     * @param callable The callable
     * @param <V> The callable type
     * @return new callable or existing if the context is missing
     */
    @NonNull
    static <V> Callable<V> wrapCurrent(@NonNull Callable<V> callable) {
        return PropagatedContext.find().map(ctx -> ctx.wrap(callable)).orElse(callable);
    }

    /**
     * Wrap supplier for this context to be propagated in.
     *
     * @param supplier The supplier
     * @param <V> The supplier type
     * @return new supplier or existing if the context is missing
     */
    @NonNull
    static <V> Supplier<V> wrapCurrent(@NonNull Supplier<V> supplier) {
        return PropagatedContext.find().map(ctx -> ctx.wrap(supplier)).orElse(supplier);
    }

    /**
     * Check if there is a context associated.
     *
     * @return true if the context exists
     */
    static boolean exists() {
        return PropagatedContextImpl.exists();
    }

    /**
     * Creates a new element with added element.
     * <p>
     * NOTE: The new element needs to be propagated.
     *
     * @param element The element element to be added
     * @return new element
     */
    @NonNull
    PropagatedContext plus(@NonNull PropagatedContextElement element);

    /**
     * Creates a new context without the provided element.
     * <p>
     * NOTE: The new context needs to be propagated.
     *
     * @param element The context element to be removed
     * @return new context
     */
    @NonNull
    PropagatedContext minus(@NonNull PropagatedContextElement element);

    /**
     * Creates a new context with replaced the provided element.
     * <p>
     * NOTE: The new context needs to be propagated.
     *
     * @param oldElement The context element to be replaced
     * @param newElement The context element to be replaced with
     * @return new context
     */
    @NonNull
    PropagatedContext replace(@NonNull PropagatedContextElement oldElement,
                              @NonNull PropagatedContextElement newElement);

    /**
     * Finds optional element of type.
     * In a case of multiple element of the same type the last one will be returned.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return optional element
     */
    <T extends PropagatedContextElement> Optional<T> find(@NonNull Class<T> elementType);

    /**
     * Find all elements of type. The first element processed by stream will be the last one added.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return stream of elements of type
     */
    <T extends PropagatedContextElement> Stream<T> findAll(@NonNull Class<T> elementType);

    /**
     * Gets element of type.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return an element or exception
     */
    <T extends PropagatedContextElement> T get(@NonNull Class<T> elementType);

    /**
     * Gets all elements.
     *
     * @return all elements.
     */
    List<PropagatedContextElement> getAllElements();

    /**
     * Propagate the context using try-resource block.
     *
     * @return auto-closeable block to be used in try-resource block.
     */
    @NonNull
    Scope propagate();

    /**
     * Wrap runnable for this context to be propagated in.
     *
     * @param runnable The runnable
     * @return new runnable
     */
    @NonNull
    default Runnable wrap(@NonNull Runnable runnable) {
        PropagatedContext propagatedContext = this;
        return () -> {
            try (Scope ignore = propagatedContext.propagate()) {
                runnable.run();
            }
        };
    }

    /**
     * Wrap callable for this context to be propagated in.
     *
     * @param callable The callable
     * @param <V>      The callable return type
     * @return new callable
     */
    @NonNull
    default <V> Callable<V> wrap(@NonNull Callable<V> callable) {
        PropagatedContext propagatedContext = this;
        return () -> {
            try (Scope ignore = propagatedContext.propagate()) {
                return callable.call();
            }
        };
    }

    /**
     * Wrap supplier for this context to be propagated in.
     *
     * @param supplier The supplier
     * @param <V>      The supplier return type
     * @return new supplier
     */
    @NonNull
    default <V> Supplier<V> wrap(@NonNull Supplier<V> supplier) {
        PropagatedContext propagatedContext = this;
        return () -> {
            try (Scope ignore = propagatedContext.propagate()) {
                return supplier.get();
            }
        };
    }

    /**
     * Propagate the context for the supplier.
     *
     * @param supplier The supplier
     * @param <V>      The supplier return type
     * @return new supplier
     */
    @NonNull
    default <V> V propagate(@NonNull Supplier<V> supplier) {
        PropagatedContext propagatedContext = this;
        try (Scope ignore = propagatedContext.propagate()) {
            return supplier.get();
        }
    }

    /**
     * Context propagation {@link AutoCloseable} to be used in try-resource block.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }

}
