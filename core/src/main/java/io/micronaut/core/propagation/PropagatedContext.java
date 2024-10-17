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
 * A mechanism for propagating state across threads that's easier to use and safer than
 * {@link ThreadLocal}.
 *
 * <p>
 * A propagated context is an immutable list of objects. {@link #plus(PropagatedContextElement)
 * Adding} or {@link #minus(PropagatedContextElement) deleting} elements from a context creates a
 * new context that must then be explicitly brought into scope by {@link #propagate() propagating
 * it}.
 *
 * <p>
 * If an element wraps an existing thread local variable then it can implement {@link
 * ThreadPropagatedContextElement} to take part in the enter-exit process.
 *
 * <p>
 * In standard usage you would call {@link #getOrEmpty()}, then {@link
 * #plus(PropagatedContextElement)} to add some data, then {@link #propagate(Supplier)} to execute a
 * lambda with the context in scope.
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
     * Captures the current context and returns a new {@link Runnable} that, when executed, will run
     * the given runnable with the captured context in scope. If no context is in scope then the
     * given callable is returned as-is.
     *
     * @param runnable The runnable
     * @return new runnable or existing if the context is missing
     */
    @NonNull
    static Runnable wrapCurrent(@NonNull Runnable runnable) {
        return PropagatedContext.find().map(ctx -> ctx.wrap(runnable)).orElse(runnable);
    }

    /**
     * Captures the current context and returns a new {@link Callable} that, when executed, will run
     * the given callable with the captured context in scope. If no context is in scope then the
     * given callable is returned as-is.
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
     * Captures the current context and returns a new {@link Supplier} that, when executed, will run
     * the given supplier with the captured context in scope. If no context is in scope then the
     * given callable is returned as-is.
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
     * Check if there is a context in scope.
     *
     * @return true if a context has been {@link #propagate() propagated}.
     */
    static boolean exists() {
        return PropagatedContextImpl.exists();
    }

    /**
     * Returns a new context extended with the given element. This doesn't add anything
     * to the existing in-scope context (if any), so you will need to propagate it
     * yourself. You can add multiple elements of the same type.
     *
     * @param element the element to be added
     * @return the new context
     */
    @NonNull
    PropagatedContext plus(@NonNull PropagatedContextElement element);

    /**
     * Returns a new context without the provided element. This doesn't remove anything
     * from the existing in-scope context (if any), so you will need to propagate it
     * yourself. Elements are compared using {@link Object#equals(Object)}.
     *
     * @param element The context element to be removed
     * @return the new context
     */
    @NonNull
    PropagatedContext minus(@NonNull PropagatedContextElement element);

    /**
     * Creates a new context with the given element replaced. This doesn't change anything
     * in the existing in-scope context (if any), so you will need to propagate it
     * yourself. Elements are compared using {@link Object#equals(Object)}.
     *
     * @param oldElement the element to be replaced
     * @param newElement the element that will replace it
     * @return the new context
     */
    @NonNull
    PropagatedContext replace(@NonNull PropagatedContextElement oldElement,
                              @NonNull PropagatedContextElement newElement);

    /**
     * Finds the first element of the given type, if any exist.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return element if found
     */
    <T extends PropagatedContextElement> Optional<T> find(@NonNull Class<T> elementType);

    /**
     * Find all elements of the given type. The first element in the stream will be the last element added.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return stream of elements of type
     */
    <T extends PropagatedContextElement> Stream<T> findAll(@NonNull Class<T> elementType);

    /**
     * Gets the first element of the given type.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return an element
     * @throws java.util.NoSuchElementException if no elements of that type are in the context.
     */
    <T extends PropagatedContextElement> T get(@NonNull Class<T> elementType);

    /**
     * Gets all elements.
     *
     * @return all elements.
     */
    List<PropagatedContextElement> getAllElements();

    /**
     * Brings this context into scope, temporarily replacing the previous context (if any). The returned
     * object must be closed to undo the propagation.
     *
     * @return auto-closeable block to be used in try-resource block.
     */
    @NonNull
    Scope propagate();

    /**
     * Returns a new runnable that runs the given runnable with this context in scope.
     *
     * @param runnable The runnable that will execute with this context in scope.
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
     * Returns a new callable that runs the given callable with this context in scope.
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
     * Returns a new supplier that runs the given supplier with this context in scope.
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
     * Executes the given supplier with this context in scope, restoring the previous context when execution completes.
     *
     * @param supplier The supplier
     * @param <V>      The supplier return type
     * @return the result of calling {@link Supplier#get()}.
     */
    @NonNull
    default <V> V propagate(@NonNull Supplier<V> supplier) {
        try (Scope ignore = propagate()) {
            return supplier.get();
        }
    }

    /**
     * Closing this object undoes the effect of calling {@link #propagate()} on a context. Intended to be used in a
     * try-with-resources block.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
