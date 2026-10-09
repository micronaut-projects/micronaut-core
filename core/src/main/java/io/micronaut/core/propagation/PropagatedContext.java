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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
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
    static PropagatedContext empty() {
        return PropagatedContextImpl.EMPTY;
    }

    /**
     * Returns the current context or an empty one.
     *
     * @return the current context or an empty one
     */
    static PropagatedContext getOrEmpty() {
        return PropagatedContextImpl.getOrEmpty();
    }

    /**
     * Returns the current context or throws an exception otherwise.
     *
     * @return the current context
     */
    static PropagatedContext get() {
        return PropagatedContextImpl.get();
    }

    /**
     * Returns an optional context.
     *
     * @return the current optional context
     */
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
    static Runnable wrapCurrent(Runnable runnable) {
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
    static <V> Callable<V> wrapCurrent(Callable<V> callable) {
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
    static <V> Supplier<V> wrapCurrent(Supplier<V> supplier) {
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
     * Whether the current propagation mode supports {@link Scope open/close scopes}, i.e. bringing a context into
     * scope with one call and taking it out of scope with a later, separate call.
     *
     * <p>Scopes are only supported by thread-local propagation (the default, {@code micronaut.propagation=thread-local}).
     * With scoped-value propagation ({@code micronaut.propagation=scoped-value}) the context is bound with a
     * {@link ScopedValue}, and a scoped value binding only exists for the dynamic extent of a single
     * {@link ScopedValue.Carrier#run(Runnable)} or {@link ScopedValue.Carrier#call(ScopedValue.CallableOp)} invocation:
     * it is tied to that stack frame and there is no way to bind it in one method and unbind it in another.
     * In that mode only the callback forms such as {@link #propagate(Supplier)}, {@link #propagate(Runnable)} and
     * {@link #propagateCall(Callable)} can be used.</p>
     *
     * <p>Integrations that cannot use a callback, because the context must stay in scope across separate calls into
     * user code (for example an {@link java.util.Iterator} that brings the context of an element into scope in
     * {@code next()} and takes it out of scope on the following {@code next()} or {@code close()}), can use this
     * method or {@link #propagateIfSupported()} to degrade gracefully instead of failing.</p>
     *
     * @return true if {@link #propagateIfSupported()} returns a scope
     * @since 5.3.0
     */
    static boolean supportsScopes() {
        return PropagatedContextConfiguration.get() == PropagatedContextConfiguration.Mode.THREAD_LOCAL;
    }

    /**
     * Is this propagated context bound? If yes the propagation is not needed.
     * @return true if bound
     * @since 5.0
     */
    boolean isBound();

    /**
     * Checks whether the context is empty.
     *
     * @return true if the context contains no elements, otherwise false
     * @since 5.0
     */
    boolean isEmpty();

    /**
     * Returns a new context extended with the given element. This doesn't add anything
     * to the existing in-scope context (if any), so you will need to propagate it
     * yourself. You can add multiple elements of the same type.
     *
     * @param element the element to be added
     * @return the new context
     */
    PropagatedContext plus(PropagatedContextElement element);

    /**
     * Returns a new context without the provided element. This doesn't remove anything
     * from the existing in-scope context (if any), so you will need to propagate it
     * yourself. Elements are compared using {@link Object#equals(Object)}.
     *
     * @param element The context element to be removed
     * @return the new context
     */
    PropagatedContext minus(PropagatedContextElement element);

    /**
     * Creates a new context with the given element replaced. This doesn't change anything
     * in the existing in-scope context (if any), so you will need to propagate it
     * yourself. Elements are compared using {@link Object#equals(Object)}.
     *
     * @param oldElement the element to be replaced
     * @param newElement the element that will replace it
     * @return the new context
     */
    PropagatedContext replace(PropagatedContextElement oldElement, PropagatedContextElement newElement);

    /**
     * Finds the first element of the given type, if any exist.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return element if found
     */
    <T extends PropagatedContextElement> Optional<T> find(Class<T> elementType);

    /**
     * Finds the last added element of the given type, like {@link #find(Class)}, but without
     * wrapping it in an {@link Optional}.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return the element or {@code null} if there is none
     * @since 5.3
     */
    default <T extends PropagatedContextElement> @Nullable T findOrNull(Class<T> elementType) {
        return find(elementType).orElse(null);
    }

    /**
     * Finds the last added element of the given type that matches the given filter. Elements are tested from the
     * last added to the first one, so this is equivalent to
     * {@code findAll(elementType).filter(filter).findFirst().orElse(null)} without creating a stream.
     *
     * @param elementType The element type
     * @param filter      The filter the element must match
     * @param <T>         The element's type
     * @return the most recently added matching element or {@code null} if there is none
     * @since 5.3.0
     */
    default <T extends PropagatedContextElement> @Nullable T findOrNull(Class<T> elementType, Predicate<? super T> filter) {
        return findAll(elementType).filter(filter).findFirst().orElse(null);
    }

    /**
     * Find all elements of the given type. The first element in the stream will be the last element added.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return stream of elements of type
     */
    <T extends PropagatedContextElement> Stream<T> findAll(Class<T> elementType);

    /**
     * Gets the first element of the given type.
     *
     * @param elementType The element type
     * @param <T> The element's type
     * @return an element
     * @throws java.util.NoSuchElementException if no elements of that type are in the context.
     */
    <T extends PropagatedContextElement> T get(Class<T> elementType);

    /**
     * Gets all elements in the order they were added. The returned list is unmodifiable.
     *
     * @return all elements.
     */
    List<PropagatedContextElement> getAllElements();

    /**
     * Brings this context into scope, temporarily replacing the previous context (if any). The returned
     * object must be closed to undo the propagation.
     *
     * @return auto-closeable block to be used in try-resource block.
     * @throws IllegalStateException if the propagation mode doesn't {@link #supportsScopes() support scopes}
     * @deprecated The method is only allowed for thread-local propagation. Prefer the callback forms such as
     * {@link #propagate(Supplier)}, or {@link #propagateIfSupported()} when the context must stay in scope across
     * separate calls.
     */
    @Deprecated(since = "5.0")
    Scope propagate();

    /**
     * Brings this context into scope if the current propagation mode {@link #supportsScopes() supports scopes},
     * temporarily replacing the previous context (if any). The returned scope must be closed, on the same thread, to
     * undo the propagation.
     *
     * <p>Unlike {@link #propagate()} this method doesn't fail with scoped-value propagation: it returns {@code null}
     * and nothing is brought into scope. It is intended for integrations that cannot use a callback, because the
     * context must stay in scope across separate calls into user code, and that would rather skip the propagation
     * than fail when it isn't possible:</p>
     *
     * <pre>{@code
     * PropagatedContext.Scope scope = context.propagateIfSupported();
     * // ... later, possibly from another method
     * if (scope != null) {
     *     scope.close();
     * }
     * }</pre>
     *
     * <p>Prefer the callback forms such as {@link #propagate(Supplier)} whenever possible, they work in both
     * propagation modes.</p>
     *
     * @return the scope to close, or {@code null} if the current propagation mode doesn't support scopes
     * @since 5.3.0
     */
    @SuppressWarnings("deprecation")
    default @Nullable Scope propagateIfSupported() {
        return supportsScopes() ? propagate() : null;
    }

    /**
     * Returns a new runnable that runs the given runnable with this context in scope.
     *
     * @param runnable The runnable that will execute with this context in scope.
     * @return new runnable
     */
    Runnable wrap(Runnable runnable);

    /**
     * Returns a new callable that runs the given callable with this context in scope.
     *
     * @param callable The callable
     * @param <V>      The callable return type
     * @return new callable
     */
    <V> Callable<V> wrap(Callable<V> callable);

    /**
     * Returns a new supplier that runs the given supplier with this context in scope.
     *
     * @param supplier The supplier
     * @param <V>      The supplier return type
     * @return new supplier
     */
    <V> Supplier<V> wrap(Supplier<V> supplier);

    /**
     * Executes the given supplier with this context in scope, restoring the previous context when execution completes.
     *
     * @param supplier The supplier
     * @param <V>      The supplier return type
     * @return the result of calling {@link Supplier#get()}.
     */
    <V> V propagate(Supplier<V> supplier);

    /**
     * Executes the given supplier with this context in scope, restoring the previous context when execution completes.
     *
     * @param callable The supplier
     * @param <V>      The supplier return type
     * @return the result of calling {@link Callable#call()}.
     * @throws Exception if an exception occurs
     * @since 5.0
     */
    <V> V propagateCall(Callable<V> callable) throws Exception;

    /**
     * Executes the given runnable with this context in scope, restoring the previous context when execution completes.
     *
     * @param runnable The runnable
     * @since 5.0
     */
    void propagate(Runnable runnable);

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
