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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    static final PropagatedContextImpl EMPTY = new PropagatedContextImpl(new PropagatedContextElement[0], false);

    private static final Scope CLEANUP = ThreadContext::remove;

    private final PropagatedContextElement[] elements;
    private final boolean containsThreadElements;

    private PropagatedContextImpl(PropagatedContextElement[] elements) {
        this(elements, containsThreadElements(elements));
    }

    private PropagatedContextImpl(PropagatedContextElement[] elements, boolean containsThreadElements) {
        this.elements = elements;
        this.containsThreadElements = containsThreadElements;
    }

    private static boolean containsThreadElements(PropagatedContextElement[] elements) {
        for (PropagatedContextElement element : elements) {
            if (isThreadElement(element)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isThreadElement(PropagatedContextElement element) {
        return element instanceof ThreadPropagatedContextElement;
    }

    public static boolean exists() {
        PropagatedContextImpl propagatedContext = ThreadContext.get();
        if (propagatedContext == null) {
            return false;
        }
        return propagatedContext.elements.length != 0;
    }

    public static PropagatedContextImpl get() {
        PropagatedContextImpl propagatedContext = ThreadContext.get();
        if (propagatedContext == null) {
            throw new IllegalStateException("No active propagation context!");
        }
        return propagatedContext;
    }

    public static Optional<PropagatedContext> find() {
        return Optional.ofNullable(ThreadContext.get());
    }

    @NonNull
    public static PropagatedContextImpl getOrEmpty() {
        PropagatedContextImpl propagatedContext = ThreadContext.get();
        if (propagatedContext == null) {
            return EMPTY;
        }
        return propagatedContext;
    }

    @Override
    public PropagatedContextImpl plus(PropagatedContextElement element) {
        PropagatedContextElement[] newElements = new PropagatedContextElement[elements.length + 1];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        newElements[newElements.length - 1] = element;
        return new PropagatedContextImpl(newElements, containsThreadElements || isThreadElement(element));
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

    @Override
    public Scope propagate() {
        PropagatedContextImpl prevCtx = ThreadContext.get();
        Scope restore;
        if (prevCtx == null && elements.length == 0) {
            return CLEANUP;
        } else if (prevCtx == null) {
            restore = CLEANUP;
        } else { // elements.length == 0
            restore = new Scope() { // Keep the anonymous class to avoid lambda in hot path
                @Override
                public void close() {
                    ThreadContext.set(prevCtx);
                }
            };
            if (elements.length == 0) {
                ThreadContext.remove();
                return restore;
            }
        }

        PropagatedContextImpl ctx = this;
        ThreadContext.set(ctx);
        if (containsThreadElements) {
            ThreadState[] threadState = ctx.updateThreadState();
            return new Scope() { // Keep the anonymous class to avoid lambda in hot path
                @Override
                public void close() {
                    ctx.restoreState(threadState);
                    if (prevCtx == null) {
                        ThreadContext.remove();
                    } else {
                        ThreadContext.set(prevCtx);
                    }
                }
            };
        }
        return restore;
    }

    private ThreadState[] updateThreadState() {
        ThreadState[] threadState = new ThreadState[elements.length];
        int index = 0;
        for (PropagatedContextElement element : elements) {
            if (isThreadElement(element)) {
                ThreadPropagatedContextElement<Object> threadPropagatedContextElement = (ThreadPropagatedContextElement<Object>) element;
                Object state = threadPropagatedContextElement.updateThreadContext();
                threadState[index++] = new ThreadState(threadPropagatedContextElement, state);
            }
        }
        return threadState;
    }

    private void restoreState(ThreadState[] threadState) {
        for (int i = threadState.length - 1; i >= 0; i--) {
            ThreadState s = threadState[i];
            if (s != null) {
                s.restore();
            }
        }
    }

    private record ThreadState(ThreadPropagatedContextElement<Object> element, Object state) {

        void restore() {
            element.restoreThreadContext(state);
        }

    }

}
