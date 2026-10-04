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
package io.micronaut.dev;

import io.micronaut.context.reload.ResourceKind;
import io.micronaut.dev.DevRuntime.SourceChanges;
import io.micronaut.dev.compile.SourceKind;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A batch waiting for the reload thread.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
class Pending {
    final Map<SourceKind, SourceChanges> sources;
    final Map<SourceKind, SourceChanges> testSources;
    final Map<ResourceKind, SourceChanges> resources;
    final boolean full;
    final @Nullable TestRequest requested;
    long sequence;

    Pending(Map<SourceKind, SourceChanges> sources, Map<ResourceKind, SourceChanges> resources, boolean full) {
        this(sources, Map.of(), resources, full, null);
    }

    Pending(Map<SourceKind, SourceChanges> sources, Map<SourceKind, SourceChanges> testSources, Map<ResourceKind, SourceChanges> resources,
            boolean full, @Nullable TestRequest requested) {
        this.sources = sources;
        this.testSources = testSources;
        this.resources = resources;
        this.full = full;
        this.requested = requested;
    }

    boolean forcesRestart() {
        return false;
    }

    boolean isEmpty() {
        return !full && !forcesRestart() && requested == null
            && sources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty())
            && testSources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty())
            && resources.values().stream().allMatch(changes -> changes.changed().isEmpty() && changes.deleted().isEmpty());
    }

    /**
     * How many tests a request runs, for merging two of them: the widest wins, every test, then the last run's
     * again, which holds the failures, then the failures alone.
     */
    private static int breadth(TestRequest request) {
        return switch (request) {
            case ALL -> 2;
            case RERUN -> 1;
            case FAILED -> 0;
        };
    }

    static Pending merge(List<Pending> batches) {
        Map<SourceKind, SourceChanges> sources = new EnumMap<>(SourceKind.class);
        Map<SourceKind, SourceChanges> testSources = new EnumMap<>(SourceKind.class);
        Map<ResourceKind, SourceChanges> resources = new EnumMap<>(ResourceKind.class);
        boolean full = false;
        boolean restart = false;
        TestRequest requested = null;
        for (Pending batch : batches) {
            batch.sources.forEach((kind, changes) -> sources.merge(kind, changes, SourceChanges::merge));
            batch.testSources.forEach((kind, changes) -> testSources.merge(kind, changes, SourceChanges::merge));
            batch.resources.forEach((kind, changes) -> resources.merge(kind, changes, SourceChanges::merge));
            full |= batch.full;
            restart |= batch.forcesRestart();
            if (batch.requested != null && (requested == null || breadth(batch.requested) > breadth(requested))) {
                requested = batch.requested;
            }
        }
        boolean forced = restart;
        return new Pending(sources, testSources, resources, full, requested) {
            @Override
            boolean forcesRestart() {
                return forced;
            }
        };
    }
}
