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

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

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
     * Creates a new context using one element.
     *
     * @param element The element to be included
     * @return the new context
     */
    @NonNull
    static PropagatedContext newContext(@NonNull PropagatedContextElement element) {
        return PropagatedContextImpl.newContext(element);
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
    static Runnable wrapCurrent(Runnable runnable) {
        return PropagatedContext.find().map(ctx -> ctx.wrap(runnable)).orElse(runnable);
    }

    /**
     * Wrap callable for this context to be propagated in.
     *
     * @param callable The callable
     * @return new callable or existing if the context is missing
     */
    @NonNull
    static <V> Callable<V> wrapCurrent(Callable<V> callable) {
        return PropagatedContext.find().map(ctx -> ctx.wrap(callable)).orElse(callable);
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
     * Creates a new context with added element.
     * <p>
     * NOTE: The new context needs to be propagated.
     *
     * @param context The context element to be added
     * @return new context
     */
    @NonNull
    PropagatedContext plus(@NonNull PropagatedContextElement context);

    /**
     * Finds optional element of type.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return optional element
     */
    <T> Optional<T> find(@NonNull Class<T> elementType);

    /**
     * Gets element of type.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return an element or exception
     */
    <T> T get(@NonNull Class<T> elementType);

    /**
     * Propagate the context using try-resource block.
     *
     * @return auto-closeable block to be used in try-resource block.
     */
    @NonNull
    InContext propagate();

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
            try (InContext ignore = propagatedContext.propagate()) {
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
            try (InContext ignore = propagatedContext.propagate()) {
                return callable.call();
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
        try (InContext ignore = propagatedContext.propagate()) {
            return supplier.get();
        }
    }

    /**
     * Context propagation {@link AutoCloseable} to be used in try-resource block.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    interface InContext extends AutoCloseable {
        @Override
        void close();
    }

}
