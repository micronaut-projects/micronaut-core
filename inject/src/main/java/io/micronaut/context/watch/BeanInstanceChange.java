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
package io.micronaut.context.watch;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Experimental;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * One batch of changes to the bean instances a watch selects: the registrations added, the ones removed, the set
 * after the batch, and which removed ones came back as a new generation of the same bean.
 *
 * @param <T> The bean type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class BeanInstanceChange<T> {

    private final List<BeanRegistration<T>> added;
    private final List<BeanRegistration<T>> removed;
    private final Collection<BeanRegistration<T>> current;
    private final boolean initial;
    private final List<Replacement<T>> replaced;

    /**
     * Creates the change.
     *
     * @param added The registrations added
     * @param removed The registrations removed
     * @param current The registrations selected after the batch
     * @param initial Whether this is the first batch
     */
    public BeanInstanceChange(List<BeanRegistration<T>> added, List<BeanRegistration<T>> removed, Collection<BeanRegistration<T>> current, boolean initial) {
        this.added = List.copyOf(Objects.requireNonNull(added, "added"));
        this.removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        this.current = List.copyOf(Objects.requireNonNull(current, "current"));
        this.initial = initial;
        this.replaced = pair(this.removed, this.added);
    }

    /**
     * @return The beans added; for the first batch, every bean selected
     */
    public List<BeanRegistration<T>> added() {
        return added;
    }

    /**
     * The beans removed, with the instances they held. An instance the watch created for a bean no scope
     * holds, such as a prototype, is destroyed by the watch once this batch was delivered: it is usable
     * only until the watcher, and the per-change handlers, returned.
     *
     * @return The beans removed, empty for the first batch
     */
    public List<BeanRegistration<T>> removed() {
        return removed;
    }

    /**
     * @return The beans selected after this batch is applied
     */
    public Collection<BeanRegistration<T>> current() {
        return current;
    }

    /**
     * @return Whether this is the first batch, describing the state when the watch was registered
     */
    public boolean initial() {
        return initial;
    }

    /**
     * @return The removed beans whose definition came back in a new generation, paired with the bean of the new one
     */
    public List<Replacement<T>> replaced() {
        return replaced;
    }

    @Override
    public String toString() {
        return "BeanInstanceChange{added=" + added.size() + ", removed=" + removed.size() + ", current=" + current.size() + ", initial=" + initial + '}';
    }

    private static <T> List<Replacement<T>> pair(List<BeanRegistration<T>> removed, List<BeanRegistration<T>> added) {
        if (removed.isEmpty() || added.isEmpty()) {
            return List.of();
        }
        List<Replacement<T>> pairs = new ArrayList<>();
        for (BeanRegistration<T> before : removed) {
            for (BeanRegistration<T> after : added) {
                if (BeanDefinitionChange.sameBean(before.getBeanDefinition(), after.getBeanDefinition())) {
                    pairs.add(new Replacement<>(before, after));
                    break;
                }
            }
        }
        return List.copyOf(pairs);
    }

    /**
     * A removed bean and the added one that replaces it: the bean of the same definition class and qualifier in
     * the new generation.
     *
     * @param before The removed bean
     * @param after Its replacement
     * @param <T> The bean type
     */
    public record Replacement<T>(BeanRegistration<T> before, BeanRegistration<T> after) {
    }
}
