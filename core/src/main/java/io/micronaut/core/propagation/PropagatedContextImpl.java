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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;

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

    static final PropagatedContextImpl EMPTY = new PropagatedContextImpl(new PropagatedContextElement[0], false, false);

    final PropagatedContextElement[] elements;
    final boolean containsThreadElements;
    final boolean containsScopedValueElements;

    private PropagatedContextImpl(PropagatedContextElement[] elements) {
        this(elements, containsThreadElements(elements), containsScopedValueElements(elements));
    }

    private PropagatedContextImpl(PropagatedContextElement[] elements, boolean containsThreadElements, boolean containsScopedValueElements) {
        this.elements = elements;
        this.containsThreadElements = containsThreadElements;
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
                if (ScopedValues.get() == this) {
                    yield supplier.get();
                } else {
                    Supplier<V> originalSupplier = supplier;
                    Supplier<V> delegate = originalSupplier;
                    if (containsThreadElements) {
                        PropagatedContextImpl self = this;
                        delegate = new Supplier<V>() {
                            @Override
                            public V get() {
                                ThreadState[] threadStates = updateThreadState(self);
                                try {
                                    return originalSupplier.get();
                                } finally {
                                    restoreState(threadStates);
                                }
                            }
                        };
                    }
                    yield ScopedValues.propagate(this, delegate);
                }
            }
            case THREAD_LOCAL -> ThreadContext.propagate(this, supplier);
        };
    }

    @Override
    public <V> V propagateCall(Callable<V> callable) throws Exception {
        return switch (PropagatedContextConfiguration.get()) {
            case SCOPED_VALUE -> {
                if (ScopedValues.get() == this) {
                    yield callable.call();
                } else {
                    Callable<V> originalCallable = callable;
                    Callable<V> delegate = originalCallable;
                    if (containsThreadElements) {
                        PropagatedContextImpl self = this;
                        delegate = new Callable<V>() { // Keep lambda for performance reasons
                            @Override
                            public V call() throws Exception {
                                ThreadState[] threadStates = updateThreadState(self);
                                try {
                                    return originalCallable.call();
                                } finally {
                                    restoreState(threadStates);
                                }
                            }
                        };
                    }
                    yield ScopedValues.propagate(this, delegate);
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
                if (ScopedValues.get() == this) {
                    runnable.run();
                } else {
                    Runnable originalRunnable = runnable;
                    Runnable delegate = originalRunnable;
                    if (containsThreadElements) {
                        PropagatedContextImpl self = this;
                        delegate = new Runnable() { // Keep lambda for performance reasons
                            @Override
                            public void run() {
                                ThreadState[] threadStates = updateThreadState(self);
                                try {
                                    originalRunnable.run();
                                } finally {
                                    restoreState(threadStates);
                                }
                            }
                        };
                    }
                    ScopedValues.propagate(this, delegate);
                }
            }
            case THREAD_LOCAL -> ThreadContext.propagate(this, runnable);
            default -> throw new IllegalStateException("Unsupported propagation mode: " + mode);
        }
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

    private static boolean containsThreadElements(PropagatedContextElement[] elements) {
        for (PropagatedContextElement element : elements) {
            if (isThreadElement(element)) {
                return true;
            }
        }
        return false;
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
            case SCOPED_VALUE -> ScopedValues.get();
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
            case SCOPED_VALUE ->
                throw new IllegalStateException("Scope propagation requires thread-local support. Set 'micronaut.propagation' to 'thread-local'.");
        };
    }

    @Override
    public PropagatedContextImpl plus(PropagatedContextElement element) {
        PropagatedContextElement[] newElements = new PropagatedContextElement[elements.length + 1];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        newElements[newElements.length - 1] = element;
        return new PropagatedContextImpl(newElements,
            containsThreadElements || isThreadElement(element),
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
    public <T extends PropagatedContextElement> Stream<T> findAll(Class<T> elementType) {
        List<PropagatedContextElement> reverseElements = new ArrayList<>(Arrays.asList(elements));
        Collections.reverse(reverseElements);
        return reverseElements.stream()
            .filter(elementType::isInstance)
            .map(elementType::cast);
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
        return new ArrayList<>(Arrays.asList(elements));
    }

    static ThreadState[] updateThreadState(PropagatedContextImpl propagatedContext) {
        ThreadState[] threadState = new ThreadState[propagatedContext.elements.length];
        int index = 0;
        for (PropagatedContextElement element : propagatedContext.elements) {
            if (element instanceof ThreadPropagatedContextElement threadPropagatedContextElement) {
                Object state = threadPropagatedContextElement.updateThreadContext();
                threadState[index++] = new ThreadState(threadPropagatedContextElement, state);
            }
        }
        return threadState;
    }

    static void restoreState(@Nullable ThreadState[] threadState) {
        for (int i = threadState.length - 1; i >= 0; i--) {
            ThreadState s = threadState[i];
            if (s != null) {
                s.restore();
            }
        }
    }

    record ThreadState(ThreadPropagatedContextElement<Object> element, @Nullable Object state) {

        void restore() {
            element.restoreThreadContext(state);
        }

    }

}
