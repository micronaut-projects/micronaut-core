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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One batch of changes to the bean definitions a watch selects: what went, what came, the set after
 * the batch, and which removed definitions came back as a new generation of the same bean.
 *
 * @param <T> The bean type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class BeanDefinitionChange<T> {

    private final List<BeanDefinition<T>> added;
    private final List<BeanDefinition<T>> removed;
    private final Collection<BeanDefinition<T>> current;
    private final boolean initial;
    private final List<Replacement<T>> replaced;

    /**
     * Creates the change.
     *
     * @param added The definitions added
     * @param removed The definitions removed
     * @param current The definitions of the watched type after the batch
     * @param initial Whether this is the startup batch
     */
    public BeanDefinitionChange(List<BeanDefinition<T>> added, List<BeanDefinition<T>> removed, Collection<BeanDefinition<T>> current, boolean initial) {
        this.added = List.copyOf(Objects.requireNonNull(added, "added"));
        this.removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        this.current = List.copyOf(Objects.requireNonNull(current, "current"));
        this.initial = initial;
        this.replaced = pair(this.removed, this.added);
    }

    /**
     * @return The definitions added; for the startup batch, every definition present
     */
    public List<BeanDefinition<T>> added() {
        return added;
    }

    /**
     * @return The definitions removed, empty for the startup batch
     */
    public List<BeanDefinition<T>> removed() {
        return removed;
    }

    /**
     * @return The definitions of the watched type after this batch is applied
     */
    public Collection<BeanDefinition<T>> current() {
        return current;
    }

    /**
     * @return Whether this is the first batch, describing the startup state
     */
    public boolean initial() {
        return initial;
    }

    /**
     * The definition that replaces a removed one: the same bean class and qualifier in the new
     * generation.
     *
     * @param removedDefinition A removed definition
     * @return Its replacement among the added ones, if any
     */
    public Optional<BeanDefinition<T>> replacementOf(BeanDefinition<T> removedDefinition) {
        for (Replacement<T> replacement : replaced) {
            if (replacement.before() == removedDefinition) {
                return Optional.of(replacement.after());
            }
        }
        return Optional.empty();
    }

    /**
     * @return The removed definitions that came back, paired with their replacements
     */
    public List<Replacement<T>> replaced() {
        return replaced;
    }

    @Override
    public String toString() {
        return "BeanDefinitionChange{added=" + added.size() + ", removed=" + removed.size() + ", current=" + current.size() + ", initial=" + initial + '}';
    }

    static <T> List<Replacement<T>> pair(List<BeanDefinition<T>> removed, List<BeanDefinition<T>> added) {
        if (removed.isEmpty() || added.isEmpty()) {
            return List.of();
        }
        List<Replacement<T>> pairs = new ArrayList<>();
        for (BeanDefinition<T> before : removed) {
            for (BeanDefinition<T> after : added) {
                if (sameBean(before, after)) {
                    pairs.add(new Replacement<>(before, after));
                    break;
                }
            }
        }
        return List.copyOf(pairs);
    }

    /**
     * Whether two definitions are generations of the same bean: the same definition class name and
     * the same declared qualifier.
     *
     * @param before One definition
     * @param after The other
     * @return True if they describe the same bean
     */
    static boolean sameBean(BeanDefinition<?> before, BeanDefinition<?> after) {
        return before.getClass().getName().equals(after.getClass().getName())
            && before.getBeanType().getName().equals(after.getBeanType().getName())
            && Objects.equals(before.getDeclaredQualifier(), after.getDeclaredQualifier());
    }

    /**
     * A removed definition and the added one that replaces it.
     *
     * @param before The removed definition
     * @param after Its replacement
     * @param <T> The bean type
     */
    public record Replacement<T>(BeanDefinition<T> before, BeanDefinition<T> after) {

        /**
         * @return Whether the annotations of the bean are the same in both generations: only bodies changed
         */
        public boolean metadataUnchanged() {
            return MetadataComparison.same(before.getAnnotationMetadata(), after.getAnnotationMetadata());
        }
    }
}
