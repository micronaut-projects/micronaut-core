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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
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
public final class ExecutableMethodChange<A extends Annotation> {

    private final List<BeanExecutableMethod<A>> added;
    private final List<BeanExecutableMethod<A>> removed;
    private final Collection<BeanExecutableMethod<A>> current;
    private final boolean initial;
    private final List<Replacement<A>> replaced;

    /**
     * Creates the change.
     *
     * @param added The methods added
     * @param removed The methods removed
     * @param current The methods carrying the annotation after the batch
     * @param initial Whether this is the first batch
     */
    public ExecutableMethodChange(List<BeanExecutableMethod<A>> added, List<BeanExecutableMethod<A>> removed, Collection<BeanExecutableMethod<A>> current, boolean initial) {
        this.added = List.copyOf(Objects.requireNonNull(added, "added"));
        this.removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        this.current = List.copyOf(Objects.requireNonNull(current, "current"));
        this.initial = initial;
        this.replaced = pair(this.removed, this.added);
    }

    /**
     * @return The methods added; for the first batch, every method selected
     */
    public List<BeanExecutableMethod<A>> added() {
        return added;
    }

    /**
     * @return The methods removed, empty for the first batch
     */
    public List<BeanExecutableMethod<A>> removed() {
        return removed;
    }

    /**
     * @return The methods carrying the annotation after this batch is applied
     */
    public Collection<BeanExecutableMethod<A>> current() {
        return current;
    }

    /**
     * @return Whether this is the first batch, describing the state when the watch was registered
     */
    public boolean initial() {
        return initial;
    }

    /**
     * The method that replaces a removed one: the same bean, name and parameter types in the new
     * generation.
     *
     * @param removedMethod A removed method
     * @return Its replacement among the added ones, if any
     */
    public Optional<BeanExecutableMethod<A>> replacementOf(BeanExecutableMethod<A> removedMethod) {
        for (Replacement<A> replacement : replaced) {
            if (replacement.before().equals(removedMethod)) {
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

    private static <A extends Annotation> List<Replacement<A>> pair(List<BeanExecutableMethod<A>> removed, List<BeanExecutableMethod<A>> added) {
        if (removed.isEmpty() || added.isEmpty()) {
            return List.of();
        }
        List<Replacement<A>> pairs = new ArrayList<>();
        for (BeanExecutableMethod<A> before : removed) {
            for (BeanExecutableMethod<A> after : added) {
                if (before.sameMethod(after)) {
                    pairs.add(new Replacement<>(before, after));
                    break;
                }
            }
        }
        return List.copyOf(pairs);
    }

    /**
     * A removed method and the added one that replaces it.
     *
     * @param before The removed method
     * @param after Its replacement
     * @param <A> The annotation type
     */
    public record Replacement<A extends Annotation>(BeanExecutableMethod<A> before, BeanExecutableMethod<A> after) {

        /**
         * Whether the annotations are the same in both generations: those of the method, of each of its
         * parameters and of its return type, type arguments included, and those of its bean, stereotypes and
         * their values included. This compares annotations only: a change of structure with the same
         * annotations, such as another return type, another generic argument or another injection point,
         * is not reported here, and a caller that derives state from structure compares that itself.
         *
         * @return Whether the annotations of the method, its parameters, its return type and its bean are the same
         */
        public boolean metadataUnchanged() {
            return MetadataComparison.sameMethod(before.method(), after.method())
                && MetadataComparison.same(before.definition().getAnnotationMetadata(), after.definition().getAnnotationMetadata());
        }
    }
}
