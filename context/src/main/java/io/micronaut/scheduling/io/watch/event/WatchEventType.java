/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.io.watch.event;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

/**
 * The watch event type. Abstracts over {@link java.nio.file.WatchEvent} since
 * the default OS X uses polling which is slow.
 *
 * @author graemerocher
 * @since 1.1.0
 */
public enum WatchEventType {
    /**
     * A file / directory was created.
     */
    CREATE,
    /**
     * A file / directory was modified.
     */
    MODIFY,
    /**
     * A file / directory was deleted.
     */
    DELETE;

    /**
     * Produces a {@link WatchEventType} for the given {@link WatchEvent#kind()}.
     *
     * @param kind The kind
     * @return The event type
     */
    public static WatchEventType of(WatchEvent.Kind kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return CREATE;
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return MODIFY;
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return DELETE;
        } else {
            throw new IllegalArgumentException("Unsupported watch event kind: " + kind);
        }
    }
}
