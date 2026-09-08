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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.Element;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Guards the default {@link io.micronaut.inject.ast.MethodElement#getMethodAnnotationMetadata()}, which
 * routes mutation back to the element it was obtained from.
 *
 * <p>An element that implements its own mutators in terms of that delegate would otherwise recurse until
 * the stack overflows. Such an element threw {@link UnsupportedOperationException} before the delegate
 * started routing, so this restores that failure in preference to a {@link StackOverflowError}.</p>
 *
 * @since 5.2.0
 */
@Internal
public final class MethodElementAnnotationsGuard {

    private static final ThreadLocal<Set<Element>> WRITING = new ThreadLocal<>();

    private MethodElementAnnotationsGuard() {
    }

    /**
     * Performs a write that routes back through the given element, failing if that element is already
     * being written to further up the stack.
     *
     * @param element   The element being written to
     * @param operation The operation, as it reads in the failure message, such as {@code "adding"}
     * @param write     The write to perform
     * @param <R>       The type of the write's result
     * @return The result of the write
     */
    public static <R> R write(Element element, String operation, Supplier<R> write) {
        Set<Element> writing = WRITING.get();
        if (writing == null) {
            writing = Collections.newSetFromMap(new IdentityHashMap<>());
            WRITING.set(writing);
        }
        if (!writing.add(element)) {
            throw new UnsupportedOperationException("Element of type [" + element.getClass() + "] does not support "
                + operation + " annotations at compilation time: its annotation mutators route back through getMethodAnnotationMetadata()");
        }
        try {
            return write.get();
        } finally {
            writing.remove(element);
            if (writing.isEmpty()) {
                WRITING.remove();
            }
        }
    }
}
