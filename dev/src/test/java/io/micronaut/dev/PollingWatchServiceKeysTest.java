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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PollingWatchServiceKeysTest {

    @TempDir
    Path directory;

    @Test
    void aDirectoryThatGoesInvalidatesItsKeyAndAFileCannotBeWatched() throws Exception {
        Path watched = Files.createDirectories(directory.resolve("watched"));
        Path file = Files.writeString(directory.resolve("file.txt"), "x");
        try (PollingWatchService service = new PollingWatchService(Duration.ofMillis(20))) {
            assertThrows(IOException.class, () -> service.register(file));
            assertNull(service.poll());
            WatchKey key = service.register(watched);

            Files.delete(watched);
            // the key is signalled once more, invalid
            assertSame(key, service.take());
            assertFalse(key.isValid());
            assertFalse(key.reset());
            assertNull(service.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void closingCancelsTheKeys() throws Exception {
        PollingWatchService service = new PollingWatchService(Duration.ofMillis(20));
        WatchKey key = service.register(directory);
        service.close();
        assertFalse(key.isValid());
        assertThrows(ClosedWatchServiceException.class, service::take);
    }
}
