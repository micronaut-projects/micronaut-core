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

import org.jspecify.annotations.NullMarked;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The changed and deleted files of one language or resource kind.
 *
 * <p>The batches of one reload come from the watcher and from whoever reports changes directly, a test harness or an
 * IDE, and the watcher reports what it saw some time ago: its event for a file written before the last reload can
 * arrive after the harness reported that file deleted, and the other way round. The order of the batches therefore
 * does not tell whether a file is there; the file system does, when the batch is compiled ({@link #settled()}).</p>
 *
 * @param changed The files added or modified
 * @param deleted The files deleted
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
record SourceChanges(Set<Path> changed, Set<Path> deleted) {
    static final SourceChanges NONE = new SourceChanges(Set.of(), Set.of());

    SourceChanges merge(SourceChanges other) {
        Set<Path> allChanged = new LinkedHashSet<>(changed);
        allChanged.addAll(other.changed);
        Set<Path> allDeleted = new LinkedHashSet<>(deleted);
        allDeleted.addAll(other.deleted);
        // the later batch wins: a file deleted then written again is a change, a file written then deleted is gone;
        // a stale event can reverse that order, which settled() corrects once the batch is compiled
        allChanged.removeAll(other.deleted);
        allDeleted.removeAll(other.changed);
        return new SourceChanges(allChanged, allDeleted);
    }

    /**
     * The changes as the file system has them now: every file reported is changed when it exists and deleted when it
     * does not, whichever event named it last. A file reported deleted that exists again is compiled, which changes
     * nothing when it is what was compiled before; a file reported written that is gone has its outputs removed rather
     * than being skipped as a source that is not there.
     *
     * @return The settled changes
     */
    SourceChanges settled() {
        Set<Path> nowChanged = new LinkedHashSet<>();
        Set<Path> nowDeleted = new LinkedHashSet<>();
        for (Set<Path> files : java.util.List.of(changed, deleted)) {
            for (Path file : files) {
                (Files.isRegularFile(file) ? nowChanged : nowDeleted).add(file);
            }
        }
        return nowChanged.equals(changed) && nowDeleted.equals(deleted) ? this : new SourceChanges(nowChanged, nowDeleted);
    }
}
