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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;

/**
 * The state each file reported to test mode was in when a batch that named it was handled, so that a report of a
 * state already handled is known for what it is.
 *
 * <p>A change reaches test mode twice: from whoever reports it directly, a test harness or an IDE, and from the
 * watcher, which polls on macOS and reports the same write some time later. A report that comes while a run is under
 * way cancels the run, since the next one covers the change, but the watcher's late report of a write the run already
 * covers is not a change: the run it cancelled would be repeated for nothing, and whoever awaited it would see it
 * cancelled. A file is in the state handled when its modification time, size and contents are those recorded, or when
 * it is absent and was recorded absent.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class HandledFiles {

    private static final Logger LOG = LoggerFactory.getLogger(HandledFiles.class);
    private static final State ABSENT = new State(-1, -1, -1);

    private final Map<Path, State> handled = new ConcurrentHashMap<>();

    /**
     * Whether every file a batch names is in the state recorded when it was last handled: a batch that names no file,
     * or asks for anything else, is news.
     *
     * @param batch The batch
     * @return Whether the batch only reports again what was handled
     */
    boolean isHandled(Pending batch) {
        if (batch.full || batch.requested != null || batch.forcesRestart()) {
            return false;
        }
        boolean any = false;
        for (Map<?, SourceChanges> changes : List.of(batch.sources, batch.testSources, batch.resources)) {
            for (SourceChanges change : changes.values()) {
                for (Set<Path> files : List.of(change.changed(), change.deleted())) {
                    for (Path file : files) {
                        any = true;
                        State recorded = handled.get(key(file));
                        if (recorded == null || !recorded.equals(State.of(file))) {
                            return false;
                        }
                    }
                }
            }
        }
        return any;
    }

    /**
     * The changes of a batch less the files already handled in their present state; the others are recorded in it.
     *
     * @param changes The changes, by kind
     * @param <K> The kind
     * @return The changes not handled yet
     */
    <K> Map<K, SourceChanges> fresh(Map<K, SourceChanges> changes) {
        Map<K, SourceChanges> fresh = new LinkedHashMap<>();
        boolean dropped = false;
        for (Map.Entry<K, SourceChanges> entry : changes.entrySet()) {
            Set<Path> changed = new LinkedHashSet<>();
            Set<Path> deleted = new LinkedHashSet<>();
            for (Path file : entry.getValue().changed()) {
                if (record(file)) {
                    changed.add(file);
                } else {
                    dropped = true;
                }
            }
            for (Path file : entry.getValue().deleted()) {
                if (record(file)) {
                    deleted.add(file);
                } else {
                    dropped = true;
                }
            }
            if (!changed.isEmpty() || !deleted.isEmpty()) {
                fresh.put(entry.getKey(), new SourceChanges(changed, deleted));
            }
        }
        if (!dropped) {
            return changes;
        }
        LOG.debug("Reports of files already handled in their present state are left out: {}", fresh);
        return fresh;
    }

    /**
     * Records a file's present state.
     *
     * @return Whether it was not the state recorded already
     */
    private boolean record(Path file) {
        State now = State.of(file);
        return !now.equals(handled.put(key(file), now));
    }

    private static Path key(Path file) {
        return file.toAbsolutePath().normalize();
    }

    /**
     * A file's state: its modification time, size and a checksum of its contents, which tells apart two writes of a
     * file system that keeps the time in seconds.
     *
     * @param modified The modification time, in milliseconds
     * @param size The size, or a negative value for a file absent or a directory
     * @param checksum The checksum of the contents
     */
    private record State(long modified, long size, long checksum) {

        static State of(Path file) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                if (!attributes.isRegularFile()) {
                    return attributes.isDirectory() ? new State(attributes.lastModifiedTime().toMillis(), -2, -2) : ABSENT;
                }
                CRC32 crc = new CRC32();
                try (InputStream in = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                        crc.update(buffer, 0, read);
                    }
                }
                return new State(attributes.lastModifiedTime().toMillis(), attributes.size(), crc.getValue());
            } catch (IOException e) {
                return ABSENT;
            }
        }
    }
}
