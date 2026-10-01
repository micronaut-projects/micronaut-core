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
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * One batch of changes to the beans a watch selects: the registrations added, the ones removed, and
 * the set after the batch.
 *
 * @param <T> The bean type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class BeanChange<T> {

    private final List<BeanRegistration<T>> added;
    private final List<BeanRegistration<T>> removed;
    private final Collection<BeanRegistration<T>> current;
    private final boolean initial;

    /**
     * Creates the change.
     *
     * @param added The registrations added
     * @param removed The registrations removed
     * @param current The registrations of the watched type after the batch
     * @param initial Whether this is the startup batch
     */
    public BeanChange(List<BeanRegistration<T>> added, List<BeanRegistration<T>> removed, Collection<BeanRegistration<T>> current, boolean initial) {
        this.added = List.copyOf(Objects.requireNonNull(added, "added"));
        this.removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        this.current = List.copyOf(Objects.requireNonNull(current, "current"));
        this.initial = initial;
    }

    /**
     * @return The beans added; for the startup batch, every bean of the type
     */
    public List<BeanRegistration<T>> added() {
        return added;
    }

    /**
     * @return The beans removed, with the instances they held, empty for the startup batch
     */
    public List<BeanRegistration<T>> removed() {
        return removed;
    }

    /**
     * @return The beans of the watched type after this batch is applied
     */
    public Collection<BeanRegistration<T>> current() {
        return current;
    }

    /**
     * @return Whether this is the first batch, describing the startup state
     */
    public boolean initial() {
        return initial;
    }

    @Override
    public String toString() {
        return "BeanChange{added=" + added.size() + ", removed=" + removed.size() + ", current=" + current.size() + ", initial=" + initial + '}';
    }
}
