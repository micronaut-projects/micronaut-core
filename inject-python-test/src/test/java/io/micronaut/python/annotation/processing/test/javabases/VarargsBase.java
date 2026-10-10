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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A Java base with inherited varargs methods, the shape of Vaadin's {@code HasComponents}:
 * {@code add(Item...)} (and the inherited default {@code addAll(Item...)}) next to the same-arity
 * {@code add(Collection)} and {@code add(String)} overloads, and a primitive varargs method after
 * a fixed parameter.
 */
public class VarargsBase implements HasItems<VarargsBase.Item> {

    private final List<String> items = new ArrayList<>();

    public void add(Item... added) {
        for (Item item : added) {
            items.add(item.name());
        }
    }

    public void add(Collection<Item> added) {
        for (Item item : added) {
            items.add("c:" + item.name());
        }
    }

    public void add(String text) {
        items.add("t:" + text);
    }

    public int sum(String label, int... values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        items.add(label + "=" + total);
        return total;
    }

    @Override
    public void addItem(String item) {
        items.add(item);
    }

    public List<String> getItems() {
        return items;
    }

    /**
     * An item added to the base.
     *
     * @param name The name
     */
    public record Item(String name) {
        @Override
        public String toString() {
            return "i:" + name;
        }
    }
}
