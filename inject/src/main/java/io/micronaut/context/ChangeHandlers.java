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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The per-change handlers of a watch request, {@code onAdded}, {@code onRemoved} and {@code onReplaced}, and their
 * delivery: once a batch is applied, each change of it is given to the handlers of its kind.
 *
 * @param <E> The type of what is watched, such as a bean definition
 * @param <R> The type of a replacement of one by its new generation
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class ChangeHandlers<E, R> {

    private final List<Consumer<? super E>> added;
    private final List<Consumer<? super E>> removed;
    private final List<Consumer<? super R>> replaced;

    ChangeHandlers() {
        this(new ArrayList<>(1), new ArrayList<>(1), new ArrayList<>(1));
    }

    private ChangeHandlers(List<Consumer<? super E>> added, List<Consumer<? super E>> removed, List<Consumer<? super R>> replaced) {
        this.added = added;
        this.removed = removed;
        this.replaced = replaced;
    }

    void onAdded(Consumer<? super E> handler) {
        added.add(Objects.requireNonNull(handler, "handler"));
    }

    void onRemoved(Consumer<? super E> handler) {
        removed.add(Objects.requireNonNull(handler, "handler"));
    }

    void onReplaced(Consumer<? super R> handler) {
        replaced.add(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * @return The handlers as they are now, for a watch: the request may be changed afterwards
     */
    ChangeHandlers<E, R> snapshot() {
        return new ChangeHandlers<>(List.<Consumer<? super E>>copyOf(added), List.<Consumer<? super E>>copyOf(removed), List.<Consumer<? super R>>copyOf(replaced));
    }

    boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && replaced.isEmpty();
    }

    /**
     * Gives the changes of an applied batch to the handlers: the removals first, then the replacements, then the
     * additions. A replacement goes to the replacement handlers when there are any, and otherwise to the removal
     * handlers, for what it replaces, and the addition handlers, for its new generation. A failing handler does not
     * stop the others.
     *
     * @param addedItems What the batch added
     * @param removedItems What the batch removed
     * @param replacements The removed items paired with the added ones that replace them
     * @param before The replaced item of a replacement
     * @param after The new generation of a replacement
     * @param failure Reports a handler that failed
     */
    void dispatch(List<E> addedItems, List<E> removedItems, List<R> replacements, Function<R, E> before, Function<R, E> after,
                  Consumer<RuntimeException> failure) {
        if (isEmpty()) {
            return;
        }
        Set<E> paired = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!replaced.isEmpty()) {
            for (R replacement : replacements) {
                paired.add(before.apply(replacement));
                paired.add(after.apply(replacement));
            }
        }
        call(removed, removedItems, paired, failure);
        if (!replaced.isEmpty()) {
            for (R replacement : replacements) {
                for (Consumer<? super R> handler : replaced) {
                    call(handler, replacement, failure);
                }
            }
        }
        call(added, addedItems, paired, failure);
    }

    private static <E> void call(List<Consumer<? super E>> handlers, List<E> items, Set<E> paired, Consumer<RuntimeException> failure) {
        if (handlers.isEmpty()) {
            return;
        }
        for (E item : items) {
            if (paired.contains(item)) {
                continue;
            }
            for (Consumer<? super E> handler : handlers) {
                call(handler, item, failure);
            }
        }
    }

    private static <T> void call(Consumer<? super T> handler, T item, Consumer<RuntimeException> failure) {
        try {
            handler.accept(item);
        } catch (RuntimeException e) {
            failure.accept(e);
        }
    }
}
