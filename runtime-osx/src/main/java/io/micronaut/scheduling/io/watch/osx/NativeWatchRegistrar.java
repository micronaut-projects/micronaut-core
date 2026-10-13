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
package io.micronaut.scheduling.io.watch.osx;

import io.methvin.watchservice.WatchablePath;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers directories with the native macOS service of directory-watcher, whose FSEvents stream of a directory
 * reports the changes of its whole tree through that directory's key. The service answers the registration of a
 * directory under one it already watches with a key that never reports, so the directory is given the key of the
 * watched one instead, and the watcher keeps that key while any directory it reports for is watched. Nor does the
 * service close a stream when its key is cancelled, while it keeps answering registrations under its directory with
 * keys that never report, so the keys are kept and handed out again to later registrations: only a directory that
 * was deleted, whose stream the service closed and whose key it cancelled, is registered natively again.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class NativeWatchRegistrar implements DirectoryWatcher.WatchKeyRegistrar {

    /**
     * The keys of the directories registered natively, by directory. Guarded by {@code this}.
     */
    private final Map<Path, WatchKey> roots = new LinkedHashMap<>();
    private final DirectoryWatcher.WatchKeyRegistrar natively;

    NativeWatchRegistrar() {
        this((directory, service) -> new WatchablePath(directory).register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        ));
    }

    /**
     * @param natively How a directory is registered with the service itself
     */
    NativeWatchRegistrar(DirectoryWatcher.WatchKeyRegistrar natively) {
        this.natively = natively;
    }

    @Override
    public synchronized WatchKey register(Path directory, WatchService watchService) throws IOException {
        // a deleted directory: the service closed its stream and forgot it
        roots.values().removeIf(key -> !key.isValid());
        for (Map.Entry<Path, WatchKey> root : roots.entrySet()) {
            if (directory.startsWith(root.getKey())) {
                return root.getValue();
            }
        }
        WatchKey key = natively.register(directory, watchService);
        roots.put(directory, key);
        return key;
    }

    @Override
    public void cancel(WatchKey key) {
        // kept: cancelling would stop no stream, and would leave the directory and its tree unwatchable for this service
    }
}
