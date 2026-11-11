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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The implementation of {@link PropagatedContext}.
 * <p>
 * Main points:
 * - Immutable design, modification requires re-propagating the context
 * - Support thread-aware context elements which can restore thread-local state
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class PropagatedContextImpl implements PropagatedContext {

    static final ThreadPropagatedContextElement<Object>[] NO_THREAD_ELEMENTS = new ThreadPropagatedContextElement[0];

    static final PropagatedContextImpl EMPTY = new PropagatedContextImpl(new PropagatedContextElement[0], NO_THREAD_ELEMENTS, false);
    /**
     * A sentinel for "no context bound", never bound or returned, distinct from {@link #EMPTY}.
     */
    static final PropagatedContextImpl UNBOUND = new PropagatedContextImpl(new PropagatedContextElement[0], NO_THREAD_ELEMENTS, false);

    static final Scope NOOP_SCOPE = () -> {
    };

    final PropagatedContextElement[] elements;
    /**
     * The {@link ThreadPropagatedContextElement}s in the order of {@link #elements}, collected once
     * so that entering and leaving the context does not scan and type check every element.
     */
    final ThreadPropagatedContextElement<Object>[] threadElements;
    final boolean containsThreadElements;
    final boolean containsScopedValueElements;

    private PropagatedContextImpl(PropagatedContextElement[] elements) {
        this(elements, threadElements(elements), containsScopedValueElements(elements));
    }

    private PropagatedContextImpl(PropagatedContextElement[] elements,
                                  ThreadPropagatedContextElement<Object>[] threadElements,
                                  boolean containsScopedValueElements) {
        this.elements = elements;
        this.threadElements = threadElements;
        this.containsThreadElements = threadElements.length != 0;
        this.containsScopedValueElements = containsScopedValueElements;
    }

    @Override
    public boolean isBound() {
        return getOrNull() == this;
    }

    @Override
    public <V> V propagate(Supplier<V> supplier) {
        return switch (PropagatedContextConfiguration.get()) {
            case SCOPED_VALUE -> {
                if (isScopedValueBound()) {
                    yield supplier.get();
                } else {
                    Supplier<V> originalSupplier = supplier;
                    Supplier<V> delegate = originalSupplier;
                    if (containsThreadElements) {
                        PropagatedContextImpl self = this;
                        delegate = new Supplier<V>() {
                            @Override
                            public V get() {
                                Object threadState = updateThreadState(self);
                                try {
                                    return originalSupplier.get();
                                } finally {
                                    restoreState(self, threadState);
                                }
                            }
                        };
                    }
                    PropagatedContext threadBound = ThreadBoundPropagation.suspend();
                    try {
                        yield ScopedValues.propagate(this, delegate);
                    } finally {
                        ThreadBoundPropagation.resume(threadBound);
                    }
                }
            }
            case THREAD_LOCAL -> ThreadContext.propagate(this, supplier);
        };
    }

    @Override
    public <V> V propagateCall(Callable<V> callable) throws Exception {
        return switch (PropagatedContextConfiguration.get()) {
            case SCOPED_VALUE -> {
                if (isScopedValueBound()) {
                    yield callable.call();
                } else {
                    Callable<V> originalCallable = callable;
                    Callable<V> delegate = originalCallable;
                    if (containsThreadElements) {
                        PropagatedContextImpl self = this;
                        delegate = new Callable<V>() { // Keep lambda for performance reasons
                            @Override
                            public V call() throws Exception {
                                Object threadState = updateThreadState(self);
                                try {
                                    return originalCallable.call();
                                } finally {
                                    restoreState(self, threadState);
                                }
                            }
                        };
                    }
                    PropagatedContext threadBound = ThreadBoundPropagation.suspend();
                    try {
                        yield ScopedValues.propagate(this, delegate);
                    } finally {
                        ThreadBoundPropagation.resume(threadBound);
                    }
                }
            }
            case THREAD_LOCAL -> ThreadContext.propagate(this, callable);
        };
    }

    @Override
    public void propagate(Runnable runnable) {
        PropagatedContextConfiguration.Mode mode = PropagatedContextConfiguration.get();
        switch (mode) {
            case SCOPED_VALUE -> {
                if (isScopedValueBound()) {
                    runnable.run();
                } else {
                    Runnable originalRunnable = runnable;
                    Runnable delegate = originalRunnable;
                    if (containsThreadElements) {
                        PropagatedContextImpl self = this;
                        delegate = new Runnable() { // Keep lambda for performance reasons
                            @Override
                            public void run() {
                                Object threadState = updateThreadState(self);
                                try {
                                    originalRunnable.run();
                                } finally {
                                    restoreState(self, threadState);
                                }
                            }
                        };
                    }
                    PropagatedContext threadBound = ThreadBoundPropagation.suspend();
                    try {
                        ScopedValues.propagate(this, delegate);
                    } finally {
                        ThreadBoundPropagation.resume(threadBound);
                    }
                }
            }
            case THREAD_LOCAL -> ThreadContext.propagate(this, runnable);
            default -> throw new IllegalStateException("Unsupported propagation mode: " + mode);
        }
    }

    /**
     * Whether this context is already in scope with scoped-value propagation, so that a callback can run directly.
     * A context only {@link ThreadBoundPropagation bound to the thread} is not, it must still be bound to the scoped
     * value so that its scoped value elements are bound and the code it runs, such as subtasks forked by a
     * {@link java.util.concurrent.StructuredTaskScope}, inherits it.
     *
     * @return true if this context is in scope
     */
    private boolean isScopedValueBound() {
        if (ScopedValues.get() != this) {
            return false;
        }
        PropagatedContext threadBound = ThreadBoundPropagation.get();
        return threadBound == null || threadBound == this;
    }

    @Override
    public <V> Callable<V> wrap(Callable<V> callable) {
        PropagatedContext propagatedContext = this;
        return () -> propagatedContext.propagateCall(callable);
    }

    @Override
    public <V> Supplier<V> wrap(Supplier<V> supplier) {
        PropagatedContext propagatedContext = this;
        return () -> propagatedContext.propagate(supplier);
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        PropagatedContext propagatedContext = this;
        return () -> propagatedContext.propagate(runnable);
    }

    @SuppressWarnings("unchecked")
    private static ThreadPropagatedContextElement<Object>[] threadElements(PropagatedContextElement[] elements) {
        int count = 0;
        for (PropagatedContextElement element : elements) {
            if (isThreadElement(element)) {
                count++;
            }
        }
        if (count == 0) {
            return NO_THREAD_ELEMENTS;
        }
        ThreadPropagatedContextElement<Object>[] threadElements = new ThreadPropagatedContextElement[count];
        int index = 0;
        for (PropagatedContextElement element : elements) {
            if (isThreadElement(element)) {
                threadElements[index++] = (ThreadPropagatedContextElement<Object>) element;
            }
        }
        return threadElements;
    }

    static boolean isThreadElement(PropagatedContextElement element) {
        return element instanceof ThreadPropagatedContextElement;
    }

    private static boolean containsScopedValueElements(PropagatedContextElement[] elements) {
        for (PropagatedContextElement element : elements) {
            if (isScopedValueElement(element)) {
                return true;
            }
        }
        return false;
    }

    static boolean isScopedValueElement(PropagatedContextElement element) {
        return element instanceof ScopedValuePropagatedContextElement;
    }

    public static boolean exists() {
        PropagatedContext propagatedContext = getOrNull();
        if (propagatedContext == null) {
            return false;
        }
        return !propagatedContext.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return elements.length == 0;
    }

    @Nullable
    public static PropagatedContext getOrNull() {
        return switch (PropagatedContextConfiguration.get()) {
            case SCOPED_VALUE -> {
                PropagatedContext threadBound = ThreadBoundPropagation.get();
                yield threadBound != null ? threadBound : ScopedValues.get();
            }
            case THREAD_LOCAL -> ThreadContext.get();
        };
    }

    public static PropagatedContext get() {
        PropagatedContext propagatedContext = getOrNull();
        if (propagatedContext == null) {
            throw new IllegalStateException("No context is present");
        }
        return propagatedContext;
    }

    public static PropagatedContext getOrEmpty() {
        PropagatedContext propagatedContext = getOrNull();
        return propagatedContext == null ? EMPTY : propagatedContext;
    }

    public static Optional<PropagatedContext> find() {
        return Optional.ofNullable(getOrNull());
    }

    @Override
    public Scope propagate() {
        return switch (PropagatedContextConfiguration.get()) {
            case THREAD_LOCAL -> ThreadContext.propagate(ThreadContext.get(), this);
            // A ScopedValue is only bound for the extent of Carrier.run/call, it cannot be bound now and unbound by a later call
            case SCOPED_VALUE ->
                throw new IllegalStateException("PropagatedContext.propagate() without a callback requires thread-local propagation. "
                    + "Use a callback such as propagate(Supplier), or propagateIfSupported(), or set 'micronaut.propagation' to 'thread-local'.");
        };
    }

    @Override
    public PropagatedContextImpl plus(PropagatedContextElement element) {
        PropagatedContextElement[] newElements = new PropagatedContextElement[elements.length + 1];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        newElements[newElements.length - 1] = element;
        ThreadPropagatedContextElement<Object>[] newThreadElements = threadElements;
        if (isThreadElement(element)) {
            newThreadElements = Arrays.copyOf(threadElements, threadElements.length + 1);
            newThreadElements[threadElements.length] = (ThreadPropagatedContextElement<Object>) element;
        }
        return new PropagatedContextImpl(newElements,
            newThreadElements,
            containsScopedValueElements || isScopedValueElement(element));
    }

    @Override
    public PropagatedContextImpl minus(PropagatedContextElement element) {
        int index = findElement(element);
        PropagatedContextElement[] newElements = new PropagatedContextElement[elements.length - 1];
        if (index > 0) {
            System.arraycopy(elements, 0, newElements, 0, index);
        }
        int next = index + 1;
        if (next != elements.length) {
            System.arraycopy(elements, next, newElements, index, elements.length - next);
        }
        return new PropagatedContextImpl(newElements);
    }

    @Override
    public PropagatedContext replace(PropagatedContextElement oldElement, PropagatedContextElement newElement) {
        int index = findElement(oldElement);
        PropagatedContextElement[] newElements = new PropagatedContextElement[elements.length];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        newElements[index] = newElement;
        return new PropagatedContextImpl(newElements);
    }

    private int findElement(PropagatedContextElement element) {
        for (int i = 0, elementsLength = elements.length; i < elementsLength; i++) {
            if (elements[i].equals(element)) {
                return i;
            }
        }
        throw new NoSuchElementException("Element is not contained in the current context!");
    }

    @Override
    public <T extends PropagatedContextElement> Optional<T> find(Class<T> elementType) {
        return Optional.ofNullable(findElement(elementType));
    }

    @Override
    public <T extends PropagatedContextElement> @Nullable T findOrNull(Class<T> elementType) {
        return findElement(elementType);
    }

    @Override
    public <T extends PropagatedContextElement> @Nullable T findOrNull(Class<T> elementType, Predicate<? super T> filter) {
        for (int i = elements.length - 1; i >= 0; i--) {
            PropagatedContextElement element = elements[i];
            if (elementType.isInstance(element)) {
                T typed = elementType.cast(element);
                if (filter.test(typed)) {
                    return typed;
                }
            }
        }
        return null;
    }

    @Override
    public <T extends PropagatedContextElement> Stream<T> findAll(Class<T> elementType) {
        if (elements.length == 0) {
            return Stream.empty();
        }
        // The elements array is never mutated, so it can be traversed in place
        return StreamSupport.stream(new ReverseElementSpliterator<>(elements, elementType), false);
    }

    @Override
    public <T extends PropagatedContextElement> T get(Class<T> elementType) {
        T element = findElement(elementType);
        if (element == null) {
            throw new NoSuchElementException();
        }
        return element;
    }

    @Nullable
    private <T extends PropagatedContextElement> T findElement(Class<T> elementType) {
        for (int i = elements.length - 1; i >= 0; i--) {
            PropagatedContextElement element = elements[i];
            if (elementType.isInstance(element)) {
                return (T) element;
            }
        }
        return null;
    }

    @Override
    public List<PropagatedContextElement> getAllElements() {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }

    /**
     * Updates the thread state of the {@link #threadElements}. With a single thread element
     * (the common case, for example the MDC) its state is returned as is, otherwise the states
     * are returned in an array in the order of {@link #threadElements}.
     *
     * @param propagatedContext The context being entered, it must contain thread elements
     * @return The state to pass to {@link #restoreState(PropagatedContextImpl, Object)}
     */
    @Nullable
    static Object updateThreadState(PropagatedContextImpl propagatedContext) {
        ThreadPropagatedContextElement<Object>[] threadElements = propagatedContext.threadElements;
        if (threadElements.length == 1) {
            return threadElements[0].updateThreadContext();
        }
        Object[] states = new Object[threadElements.length];
        int index = 0;
        try {
            for (; index < threadElements.length; index++) {
                states[index] = threadElements[index].updateThreadContext();
            }
        } catch (RuntimeException | Error e) {
            // restore the elements that were updated before the failure, the update failure wins
            restore(threadElements, states, index - 1, e);
            throw e;
        }
        return states;
    }

    /**
     * Restores the thread state returned by {@link #updateThreadState(PropagatedContextImpl)},
     * in the reverse order of the update.
     *
     * @param propagatedContext The context being left
     * @param threadState       The state returned on entry
     */
    static void restoreState(PropagatedContextImpl propagatedContext, @Nullable Object threadState) {
        ThreadPropagatedContextElement<Object>[] threadElements = propagatedContext.threadElements;
        if (threadElements.length == 1) {
            threadElements[0].restoreThreadContext(threadState);
            return;
        }
        Object[] states = (Object[]) Objects.requireNonNull(threadState);
        Throwable failure = restore(threadElements, states, threadElements.length - 1, null);
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /**
     * Restores the elements from {@code fromIndex} down to the first. Every element is restored
     * even if one fails: the first failure, or the given one, is returned with the later ones
     * suppressed.
     */
    @Nullable
    private static Throwable restore(ThreadPropagatedContextElement<Object>[] threadElements,
                                     Object[] states,
                                     int fromIndex,
                                     @Nullable Throwable failure) {
        for (int i = fromIndex; i >= 0; i--) {
            try {
                threadElements[i].restoreThreadContext(states[i]);
            } catch (RuntimeException | Error e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }

    /**
     * Traverses the elements of the given type from the last added to the first one without copying the array.
     *
     * @param <T> The element type
     */
    private static final class ReverseElementSpliterator<T extends PropagatedContextElement> implements Spliterator<T> {

        private final PropagatedContextElement[] elements;
        private final Class<T> elementType;
        private int index;

        ReverseElementSpliterator(PropagatedContextElement[] elements, Class<T> elementType) {
            this.elements = elements;
            this.elementType = elementType;
            this.index = elements.length - 1;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            while (index >= 0) {
                PropagatedContextElement element = elements[index--];
                if (elementType.isInstance(element)) {
                    action.accept(elementType.cast(element));
                    return true;
                }
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            for (int i = index; i >= 0; i--) {
                PropagatedContextElement element = elements[i];
                if (elementType.isInstance(element)) {
                    action.accept(elementType.cast(element));
                }
            }
            index = -1;
        }

        @Override
        public @Nullable Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return (long) index + 1;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL | IMMUTABLE;
        }
    }

}
