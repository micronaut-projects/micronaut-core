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
package io.micronaut.scheduling.io.watch;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.scheduling.io.watch.event.WatchEventType;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One changed file or directory.
 *
 * <p>Several events for the same path within one quiet period, or while the listener of a registration is
 * still working on its previous batch, are coalesced into one change: a file
 * created and then modified is reported as created, a file modified and then deleted as deleted, and a
 * file deleted and then created again as modified. A file created and then deleted is not reported at all,
 * since no listener was told it existed.</p>
 *
 * @param path The absolute path of the file or directory
 * @param type The kind of change
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public record FileChange(Path path, WatchEventType type) {

    /**
     * Validating constructor.
     *
     * @param path The absolute path
     * @param type The kind of change
     */
    public FileChange {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
    }

    /**
     * Coalesces a later event for the same path into this change.
     *
     * @param later The type of the later event
     * @return The change that describes both events, or null when they cancel out
     */
    @Nullable FileChange merge(WatchEventType later) {
        if (type == WatchEventType.CREATE && later == WatchEventType.DELETE) {
            return null;
        }
        WatchEventType merged = switch (type) {
            case CREATE -> WatchEventType.CREATE;
            case MODIFY -> later;
            case DELETE -> later == WatchEventType.DELETE ? WatchEventType.DELETE : WatchEventType.MODIFY;
        };
        return merged == type ? this : new FileChange(path, merged);
    }
}
