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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native macOS watch service: what it reports, and that opening and closing it repeatedly, and dropping it
 * unclosed, while FSEvents keeps reporting writes, never crashes the JVM, as the library's service did once the
 * garbage collector freed a callback FSEvents still called.
 */
@EnabledOnOs(OS.MAC)
class FsEventsWatchServiceTest {

    @TempDir
    Path base;

    @Test
    void reportsCreatedModifiedAndDeletedEntriesOfEachDirectorySoon() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path file = Files.writeString(nested.resolve("a.txt"), "0");
        try (FsEventsWatchService service = new FsEventsWatchService(DevWatchService.MAC_LATENCY_SECONDS, java.time.Duration.ofMillis(DevWatchService.POLL_MILLIS))) {
            service.register(root);
            WatchKey nestedKey = service.register(nested);

            long start = System.nanoTime();
            Files.writeString(file, "1");
            assertEquals(Set.of("ENTRY_MODIFY a.txt"), events(service, nestedKey));
            // far sooner than the interval at which every directory is compared
            assertTrue(System.nanoTime() - start < FsEventsWatchService.SAFETY_INTERVAL.toNanos() / 2, "reported late");

            Files.writeString(nested.resolve("b.txt"), "0");
            assertEquals(Set.of("ENTRY_CREATE b.txt"), events(service, nestedKey));
            Files.delete(file);
            assertEquals(Set.of("ENTRY_DELETE a.txt"), events(service, nestedKey));
        }
    }

    @Test
    void openingClosingAndDroppingServicesWhileWritesAreReportedNeverCrashes() throws Exception {
        List<Path> directories = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            directories.add(Files.createDirectories(base.resolve("dir" + i)));
            Files.createDirectories(base.resolve("dir" + i + "/sub"));
        }
        // those of other tests' runtimes in this JVM
        long runLoopsBefore = runLoops();
        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicInteger written = new AtomicInteger();
        Thread writer = new Thread(() -> {
            while (writing.get()) {
                int n = written.incrementAndGet();
                Path directory = directories.get(n % directories.size()).resolve(n % 2 == 0 ? "sub" : ".");
                try {
                    Files.writeString(directory.resolve("f" + (n % 7) + ".txt"), Integer.toString(n));
                    if (n % 11 == 0) {
                        Files.deleteIfExists(directory.resolve("f" + (n % 7) + ".txt"));
                    }
                    Thread.sleep(1);
                } catch (Exception e) {
                    // the next write
                }
            }
        }, "writer");
        writer.setDaemon(true);
        writer.start();
        int reported = 0;
        try {
            for (int round = 0; round < 150; round++) {
                FsEventsWatchService service = new FsEventsWatchService(DevWatchService.MAC_LATENCY_SECONDS, java.time.Duration.ofMillis(DevWatchService.POLL_MILLIS));
                for (Path directory : directories) {
                    service.register(directory);
                    service.register(directory.resolve("sub"));
                }
                WatchKey key = service.poll(20, TimeUnit.MILLISECONDS);
                if (key != null) {
                    reported++;
                    key.pollEvents();
                    key.reset();
                }
                if (round % 3 == 0) {
                    // closed from another thread while the run loop reports
                    Thread closer = new Thread(service::close);
                    closer.start();
                    closer.join();
                } else if (round % 3 == 1) {
                    service.close();
                }
                // and every third one dropped unclosed: its run loop keeps it, and its callbacks, until the process ends
                if (round % 10 == 0) {
                    System.gc();
                }
            }
            System.gc();
            Thread.sleep(200);
        } finally {
            writing.set(false);
            writer.join();
        }
        assertTrue(written.get() > 100);
        assertTrue(reported > 0, "no write was reported");
        // the services dropped unclosed are closed once unreachable, their run loops ended
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (runLoops() > runLoopsBefore && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(100);
        }
        assertEquals(runLoopsBefore, runLoops(), "run loops left");
    }

    private static long runLoops() {
        return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread.getName().equals("micronaut-dev-fsevents")).count();
    }

    private static Set<String> events(FsEventsWatchService service, WatchKey expected) throws InterruptedException {
        Set<String> events = new HashSet<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (events.isEmpty() && System.nanoTime() < deadline) {
            WatchKey key = service.poll(100, TimeUnit.MILLISECONDS);
            if (key != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    // the parent's key sees the directory's own modification time change
                    if (key == expected) {
                        events.add(event.kind().name() + " " + event.context());
                    }
                }
                key.reset();
            }
        }
        return events;
    }
}
