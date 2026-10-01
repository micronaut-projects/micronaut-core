package io.micronaut.dev;

import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileChangeBatch;
import io.micronaut.scheduling.io.watch.WatchOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollingWatchServiceTest {

    @TempDir
    Path directory;

    @Test
    void aComparisonReportsCreatedModifiedAndDeletedEntriesRelativeToTheDirectory() throws Exception {
        Path existing = Files.writeString(directory.resolve("existing.py"), "a");
        try (PollingWatchService service = new PollingWatchService(Duration.ofMillis(20))) {
            WatchKey key = service.register(directory);
            assertSame(key, service.register(directory));
            assertEquals(directory.toAbsolutePath().normalize(), key.watchable());

            Files.writeString(directory.resolve("created.py"), "b");
            assertEquals(Set.of("ENTRY_CREATE created.py"), events(service.poll(5, TimeUnit.SECONDS)));
            assertTrue(key.reset());

            Files.writeString(existing, "changed");
            Files.setLastModifiedTime(existing, FileTime.from(Instant.now().plusSeconds(5)));
            Files.delete(directory.resolve("created.py"));
            Set<String> seen = new java.util.HashSet<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (seen.size() < 2 && System.nanoTime() < deadline) {
                WatchKey signalled = service.poll(1, TimeUnit.SECONDS);
                if (signalled != null) {
                    seen.addAll(events(signalled));
                    signalled.reset();
                }
            }
            assertEquals(Set.of("ENTRY_MODIFY existing.py", "ENTRY_DELETE created.py"), seen);

            // nothing changed: no key is signalled
            assertNull(service.poll(100, TimeUnit.MILLISECONDS));
            key.cancel();
            assertFalse(key.isValid());
            Files.writeString(directory.resolve("ignored.py"), "c");
            assertNull(service.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void theDirectoryWatcherWatchesWithItAndAClosedServiceRefusesToBePolled() throws Exception {
        PollingWatchService service = new PollingWatchService(Duration.ofMillis(20));
        LinkedBlockingQueue<FileChangeBatch> batches = new LinkedBlockingQueue<>();
        try (DirectoryWatcher watcher = DirectoryWatcher.builder(service)
            .registrar((dir, watchService) -> service.register(dir))
            .quietPeriod(Duration.ofMillis(50))
            .build()
            .start()) {
            watcher.watch(directory, WatchOptions.DEFAULT, batches::add);
            Path nested = Files.createDirectories(directory.resolve("pkg"));
            Files.writeString(nested.resolve("module.py"), "x");
            Path changed = directory.resolve("pkg").resolve("module.py").toAbsolutePath().normalize();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            boolean found = false;
            while (!found && System.nanoTime() < deadline) {
                FileChangeBatch batch = batches.poll(1, TimeUnit.SECONDS);
                found = batch != null && batch.changes().stream().anyMatch(change -> change.path().equals(changed));
            }
            assertTrue(found, "a file created in a directory created after the start is reported");
        }
        service.close();
        assertThrows(ClosedWatchServiceException.class, service::poll);
        assertThrows(ClosedWatchServiceException.class, () -> service.register(directory));
    }

    private static Set<String> events(WatchKey key) {
        assertNotNull(key, "a change is reported within the timeout");
        List<WatchEvent<?>> events = key.pollEvents();
        assertTrue(events.stream().allMatch(event -> event.count() == 1));
        assertTrue(events.stream().map(WatchEvent::kind).allMatch(kind -> kind == StandardWatchEventKinds.ENTRY_CREATE
            || kind == StandardWatchEventKinds.ENTRY_MODIFY || kind == StandardWatchEventKinds.ENTRY_DELETE));
        return events.stream().map(event -> event.kind().name() + " " + event.context()).collect(Collectors.toSet());
    }
}
