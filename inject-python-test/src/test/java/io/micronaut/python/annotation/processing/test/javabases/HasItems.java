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
package io.micronaut.python.annotation.processing.test.javabases;

/**
 * An interface declaring a default varargs method next to a same-arity overload, the shape of
 * Vaadin's {@code HasComponents.add(Component...)}. Its {@code addFirst(T)} takes the type variable that an
 * implementing class resolves, as Vaadin's {@code HasComponentsOfType<T>.addComponentAsFirst(T)} does through
 * {@code HasComponents extends HasComponentsOfType<Component>}, and its {@code addEach(T[])} an array of it.
 *
 * @param <T> The item type
 */
public interface HasItems<T> {

    void addItem(String item);

    default void addAll(T... items) {
        for (T item : items) {
            addItem(String.valueOf(item));
        }
    }

    default void addAll(String text) {
        addItem("t:" + text);
    }

    default void addFirst(T item) {
        addItem("f:" + item);
    }

    default void addEach(T[] items) {
        for (T item : items) {
            addItem("e:" + item);
        }
    }
}
