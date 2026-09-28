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
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One batch of changes to the executable methods carrying an annotation: what went, what came, the
 * set after the batch, and which removed methods came back as a new generation of the same method.
 *
 * @param <A> The annotation type watched
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ExecutableMethodChange<A extends Annotation> {

    private final List<Entry<A>> added;
    private final List<Entry<A>> removed;
    private final Collection<Entry<A>> current;
    private final boolean initial;
    private final List<Replacement<A>> replaced;

    /**
     * Creates the change.
     *
     * @param added The methods added
     * @param removed The methods removed
     * @param current The methods carrying the annotation after the batch
     * @param initial Whether this is the startup batch
     */
    public ExecutableMethodChange(List<Entry<A>> added, List<Entry<A>> removed, Collection<Entry<A>> current, boolean initial) {
        this.added = List.copyOf(Objects.requireNonNull(added, "added"));
        this.removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        this.current = List.copyOf(Objects.requireNonNull(current, "current"));
        this.initial = initial;
        this.replaced = pair(this.removed, this.added);
    }

    /**
     * @return The methods added; for the startup batch, every method carrying the annotation
     */
    public List<Entry<A>> added() {
        return added;
    }

    /**
     * @return The methods removed, empty for the startup batch
     */
    public List<Entry<A>> removed() {
        return removed;
    }

    /**
     * @return The methods carrying the annotation after this batch is applied
     */
    public Collection<Entry<A>> current() {
        return current;
    }

    /**
     * @return Whether this is the first batch, describing the startup state
     */
    public boolean initial() {
        return initial;
    }

    /**
     * The method that replaces a removed one: the same bean, name and parameter types in the new
     * generation.
     *
     * @param removedEntry A removed method
     * @return Its replacement among the added ones, if any
     */
    public Optional<Entry<A>> replacementOf(Entry<A> removedEntry) {
        for (Replacement<A> replacement : replaced) {
            if (replacement.before().equals(removedEntry)) {
                return Optional.of(replacement.after());
            }
        }
        return Optional.empty();
    }

    /**
     * @return The removed methods that came back, paired with their replacements
     */
    public List<Replacement<A>> replaced() {
        return replaced;
    }

    @Override
    public String toString() {
        return "ExecutableMethodChange{added=" + added.size() + ", removed=" + removed.size() + ", current=" + current.size() + ", initial=" + initial + '}';
    }

    private static <A extends Annotation> List<Replacement<A>> pair(List<Entry<A>> removed, List<Entry<A>> added) {
        if (removed.isEmpty() || added.isEmpty()) {
            return List.of();
        }
        List<Replacement<A>> pairs = new ArrayList<>();
        for (Entry<A> before : removed) {
            for (Entry<A> after : added) {
                if (before.sameMethod(after)) {
                    pairs.add(new Replacement<>(before, after));
                    break;
                }
            }
        }
        return List.copyOf(pairs);
    }

    /**
     * A method of a bean.
     *
     * @param definition The bean's definition
     * @param method The method
     * @param <A> The annotation type
     */
    public record Entry<A extends Annotation>(BeanDefinition<?> definition, ExecutableMethod<?, ?> method) {

        /**
         * Validating constructor.
         *
         * @param definition The definition
         * @param method The method
         */
        public Entry {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(method, "method");
        }

        /**
         * Whether another entry is a generation of the same method: same bean, name and parameter types.
         *
         * @param other The other entry
         * @return True if it is
         */
        public boolean sameMethod(Entry<?> other) {
            return BeanDefinitionChange.sameBean(definition, other.definition)
                && method.getMethodName().equals(other.method.getMethodName())
                && Arrays.equals(typeNames(method), typeNames(other.method));
        }

        private static String[] typeNames(ExecutableMethod<?, ?> method) {
            Class<?>[] types = method.getArgumentTypes();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = types[i].getName();
            }
            return names;
        }
    }

    /**
     * A removed method and the added one that replaces it.
     *
     * @param before The removed method
     * @param after Its replacement
     * @param <A> The annotation type
     */
    public record Replacement<A extends Annotation>(Entry<A> before, Entry<A> after) {

        /**
         * @return Whether the annotations of the method and its bean are the same in both generations: only bodies changed
         */
        public boolean metadataUnchanged() {
            return MetadataComparison.same(before.method().getAnnotationMetadata(), after.method().getAnnotationMetadata())
                && MetadataComparison.same(before.definition().getAnnotationMetadata(), after.definition().getAnnotationMetadata());
        }
    }
}
