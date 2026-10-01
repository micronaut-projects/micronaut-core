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
package io.micronaut.dev.change;

import io.micronaut.context.reload.ClassChange;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What changed between two {@link OutputSnapshot}s.
 *
 * @param classes The classes added, modified or removed
 * @param changedResources The relative paths of resources added or modified
 * @param removedResources The relative paths of resources removed
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record ChangeSet(List<ClassChange> classes, Set<String> changedResources, Set<String> removedResources) {

    /**
     * Validating constructor.
     *
     * @param classes The class changes
     * @param changedResources The changed resources
     * @param removedResources The removed resources
     */
    public ChangeSet {
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        changedResources = Set.copyOf(Objects.requireNonNull(changedResources, "changedResources"));
        removedResources = Set.copyOf(Objects.requireNonNull(removedResources, "removedResources"));
    }

    /**
     * @return Whether nothing changed
     */
    public boolean isEmpty() {
        return classes.isEmpty() && changedResources.isEmpty() && removedResources.isEmpty();
    }

    /**
     * @return Whether any class changed
     */
    public boolean hasClassChanges() {
        return !classes.isEmpty();
    }

    /**
     * @return The binary names of the changed classes
     */
    public Set<String> classNames() {
        return classes.stream().map(ClassChange::className).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
