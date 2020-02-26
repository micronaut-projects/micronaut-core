/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling.io.watch.event;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.concurrent.Immutable;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Event fired when a file that is being watched changes.
 *
 * @author graemerocher
 * @since 1.1.0
 * @see io.micronaut.scheduling.io.watch.FileWatchConfiguration
 */
@Immutable
public class FileChangedEvent extends ApplicationEvent {
    private final Path path;
    private final WatchEventType eventType;

    /**
     * Constructs a new file changed event.
     *
     * @param path The path
     * @param eventType The event type
     */
    public FileChangedEvent(@NonNull Path path, @NonNull WatchEventType eventType) {
        super(path);
        ArgumentUtils.requireNonNull("path", path);
        ArgumentUtils.requireNonNull("eventType", eventType);
        this.path = path;
        this.eventType = eventType;
    }

    /**
     * Constructs a new file changed event.
     *
     * @param path The path
     * @param eventType The event type
     */
    public FileChangedEvent(@NonNull Path path, @NonNull WatchEvent.Kind eventType) {
        this(path, WatchEventType.of(eventType));
    }

    @Override
    public @NonNull Path getSource() {
        return (Path) super.getSource();
    }

    /**
     * The path to the file / directory that changed.
     *
     * @return The path
     */
    public @NonNull Path getPath() {
        return path;
    }

    /**
     * The watch event type.
     * @return The event type
     */
    public @NonNull WatchEventType getEventType() {
        return eventType;
    }
}
