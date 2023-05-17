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
import io.micronaut.core.annotation.NonNull;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
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

    private static final ThreadLocal<PropagatedContextImpl> THREAD_CONTEXT = new ThreadLocal<>() {
        @Override
        public String toString() {
            return "Micronaut Propagation Context";
        }
    };

    private static final Scope CLEANUP = THREAD_CONTEXT::remove;

    private static final PropagatedContextImpl EMPTY = new PropagatedContextImpl(Collections.emptyList());

    private final List<PropagatedContextElement> elements;
    private final boolean containsThreadElements;

    private PropagatedContextImpl(List<PropagatedContextElement> elements) {
        this.elements = elements;
        boolean containsThreadElements = false;
        for (PropagatedContextElement element : elements) {
            if (element instanceof ThreadPropagatedContextElement) {
                containsThreadElements = true;
                break;
            }
        }
        this.containsThreadElements = containsThreadElements;
    }

    public static PropagatedContextImpl newContext(PropagatedContextElement element) {
        return new PropagatedContextImpl(Collections.singletonList(element));
    }

    public static boolean exists() {
        PropagatedContextImpl propagatedContext = PropagatedContextImpl.THREAD_CONTEXT.get();
        if (propagatedContext == null) {
            return false;
        }
        return !propagatedContext.elements.isEmpty();
    }

    public static PropagatedContextImpl get() {
        PropagatedContextImpl propagatedContext = THREAD_CONTEXT.get();
        if (propagatedContext == null) {
            throw new IllegalStateException("No active propagation context!");
        }
        return propagatedContext;
    }

    public static Optional<PropagatedContext> find() {
        return Optional.ofNullable(THREAD_CONTEXT.get());
    }

    @NonNull
    public static PropagatedContextImpl getOrEmpty() {
        PropagatedContextImpl propagatedContext = THREAD_CONTEXT.get();
        if (propagatedContext == null) {
            return EMPTY;
        }
        return propagatedContext;
    }

    @Override
    public PropagatedContextImpl plus(PropagatedContextElement element) {
        ArrayList<PropagatedContextElement> newElements = new ArrayList<>(elements.size() + 1);
        newElements.addAll(elements);
        newElements.add(element);
        return new PropagatedContextImpl(Collections.unmodifiableList(newElements));
    }

    @Override
    public PropagatedContextImpl minus(PropagatedContextElement element) {
        ArrayList<PropagatedContextElement> newElements = new ArrayList<>(elements);
        if (!newElements.remove(element)) {
            throw new NoSuchElementException("Element is not contained in the current context!");
        }
        return new PropagatedContextImpl(Collections.unmodifiableList(newElements));
    }

    @Override
    public PropagatedContext replace(PropagatedContextElement oldElement, PropagatedContextElement newElement) {
        ArrayList<PropagatedContextElement> newElements = new ArrayList<>(elements);
        int index = newElements.indexOf(oldElement);
        if (index < 0) {
            throw new NoSuchElementException("Element is not contained in the current context!");
        }
        newElements.set(index, newElement);
        return new PropagatedContextImpl(Collections.unmodifiableList(newElements));
    }

    @Override
    public <T extends PropagatedContextElement> Optional<T> find(Class<T> elementType) {
        return Optional.ofNullable(findElement(elementType));
    }

    @Override
    public <T extends PropagatedContextElement> Stream<T> findAll(Class<T> elementType) {
        List<PropagatedContextElement> reverseElements = new ArrayList<>(this.elements);
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

    private <T extends PropagatedContextElement> T findElement(Class<T> elementType) {
        ListIterator<PropagatedContextElement> listIterator = elements.listIterator(elements.size());
        while (listIterator.hasPrevious()) {
            PropagatedContextElement element = listIterator.previous();
            if (elementType.isInstance(element)) {
                return (T) element;
            }
        }
        return null;
    }

    @Override
    public List<PropagatedContextElement> getAllElements() {
        return elements;
    }

    @Override
    public Scope propagate() {
        PropagatedContextImpl prevCtx = THREAD_CONTEXT.get();
        Scope restore = prevCtx == null ? CLEANUP : () -> THREAD_CONTEXT.set(prevCtx);
        if (prevCtx == this || elements.isEmpty()) {
            return restore;
        }
        PropagatedContextImpl ctx = this;
        THREAD_CONTEXT.set(ctx);
        if (containsThreadElements) {
            List<Map.Entry<ThreadPropagatedContextElement<Object>, Object>> threadState = ctx.updateThreadState();
            return () -> {
                ctx.restoreState(threadState);
                if (prevCtx == null) {
                    THREAD_CONTEXT.remove();
                } else {
                    THREAD_CONTEXT.set(prevCtx);
                }
            };
        }
        return restore;
    }

    private List<Map.Entry<ThreadPropagatedContextElement<Object>, Object>> updateThreadState() {
        List<Map.Entry<ThreadPropagatedContextElement<Object>, Object>> threadState = new ArrayList<>(elements.size());
        for (PropagatedContextElement element : elements) {
            if (element instanceof ThreadPropagatedContextElement) {
                ThreadPropagatedContextElement<Object> threadPropagatedContextElement = (ThreadPropagatedContextElement<Object>) element;
                Object state = threadPropagatedContextElement.updateThreadContext();
                threadState.add(new AbstractMap.SimpleEntry<>(threadPropagatedContextElement, state));
            }
        }
        return threadState;
    }

    private void restoreState(List<Map.Entry<ThreadPropagatedContextElement<Object>, Object>> threadState) {
        for (Map.Entry<ThreadPropagatedContextElement<Object>, Object> e : threadState) {
            ThreadPropagatedContextElement<Object> threadPropagatedContextElement = e.getKey();
            threadPropagatedContextElement.restoreThreadContext(e.getValue());
        }
    }

}
