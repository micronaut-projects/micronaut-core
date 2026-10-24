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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native macOS watch service on any platform, against a fake FSEvents: a reported directory is compared at once,
 * the comparisons fall back to the polling interval when FSEvents cannot watch a directory or its run loop ends, and
 * a service dropped unclosed is closed once unreachable.
 */
class FsEventsWatchServiceLifecycleTest {

    private static final Duration FALLBACK = Duration.ofMillis(DevWatchService.POLL_MILLIS);
    private static final Duration LATENCY = Duration.ofMillis(50);

    @TempDir
    Path base;

    @Test
    void aChangeMadeWhileTheStreamStartsIsReportedRatherThanTakenForTheFirstState() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        // after the directory is read and before the stream reports anything: no event announces this change
        fake.onStart = () -> {
            try {
                Files.writeString(root.resolve("during.txt"), "x");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        try (FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK)) {
            WatchKey rootKey = service.register(root);
            assertTrue(Files.exists(root.resolve("during.txt")));
            // compared once the stream runs, before registering returns
            WatchKey key = service.poll();
            assertSame(rootKey, key);
            List<WatchEvent<?>> events = key.pollEvents();
            assertEquals("ENTRY_CREATE during.txt", events.get(0).kind().name() + " " + events.get(0).context());
        }
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aReportedDirectoryIsComparedAtOnceRatherThanAtTheSafetyInterval() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        String realNested = nested.toRealPath().toString();
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK)) {
            service.register(root);
            WatchKey nestedKey = service.register(nested);
            // one stream for the tree
            assertEquals(1, fake.streams.size());
            assertEquals(LATENCY, service.detectionDelay(LATENCY));
            assertNull(service.poll());

            Files.writeString(nested.resolve("a.txt"), "a");
            fake.callBack(events -> events.report(new String[] {realNested}, new int[] {0}));
            WatchKey key = service.poll(FsEventsWatchService.SAFETY_INTERVAL.toMillis() / 2, TimeUnit.MILLISECONDS);
            assertSame(nestedKey, key);
            List<WatchEvent<?>> events = key.pollEvents();
            assertEquals("ENTRY_CREATE a.txt", events.get(0).kind().name() + " " + events.get(0).context());
            key.reset();

            Files.writeString(nested.resolve("b.txt"), "b");
            // the tree is compared as a whole: the root's key sees the nested directory's modification time change too
            fake.callBack(FsEventStreams.Events::rescan);
            Set<WatchKey> signalled = new HashSet<>();
            signalled.add(service.take());
            WatchKey next = service.poll(1, TimeUnit.SECONDS);
            if (next != null) {
                signalled.add(next);
            }
            assertTrue(signalled.contains(nestedKey), "the nested directory was not compared");
        }
        assertTrue(fake.streams.get(0).released);
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aDirectoryFsEventsCannotWatchIsComparedAtThePollingInterval() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK)) {
            fake.createFailure = new IOException("too many streams");
            WatchKey key = service.register(root);
            assertEquals(FALLBACK, service.detectionDelay(LATENCY));
            // a second failure changes nothing
            service.register(Files.createDirectories(base.resolve("other")));
            assertEquals(FALLBACK, service.detectionDelay(LATENCY));

            Files.writeString(root.resolve("a.txt"), "a");
            // no report: the comparisons at the polling interval notice it
            assertSame(key, service.poll(FsEventsWatchService.SAFETY_INTERVAL.toMillis() / 2, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void theComparisonsFallBackToThePollingIntervalWhenTheRunLoopEnds() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK)) {
            fake.runFailure = new IllegalStateException("the run loop died");
            service.register(root);
            fake.runLoopThread.join(5000);
            assertFalse(fake.runLoopThread.isAlive());
            assertEquals(FALLBACK, service.detectionDelay(LATENCY));
            // a later registration falls back as well, and is still compared
            Path other = Files.createDirectories(base.resolve("other"));
            WatchKey otherKey = service.register(other);
            Files.writeString(other.resolve("a.txt"), "a");
            assertSame(otherKey, service.poll(FsEventsWatchService.SAFETY_INTERVAL.toMillis() / 2, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void aClosedServiceRefusesRegistrations() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK);
        service.close();
        assertThrows(ClosedWatchServiceException.class, () -> service.register(root));
        assertFalse(fake.runLoopThread.isAlive());
        // closing again does nothing
        service.close();
    }

    @Test
    void aServiceWhoseRunLoopCannotStartIsNotCreated() {
        FakeFsEvents fake = new FakeFsEvents();
        fake.runLoopLinkageFailure = new UnsatisfiedLinkError("no CoreServices");
        assertThrows(IOException.class, () -> new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK));
    }

    @Test
    void aServiceDroppedUnclosedIsClosedOnceUnreachable() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        register(fake, root);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (fake.runLoopThread.isAlive() && System.nanoTime() < deadline) {
            System.gc();
            fake.runLoopThread.join(100);
        }
        assertFalse(fake.runLoopThread.isAlive(), "the run loop of the dropped service still runs");
        FakeFsEvents.Stream stream = fake.streams.get(0);
        assertTrue(stream.released);
        assertFalse(FsEventStreams.isLive(stream));
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void theDevelopmentWatchServiceWaitsForTheLatencyThenThePollingInterval() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK);
        DevWatchService watch = new DevWatchService(service, (directory, watchService) -> service.register(directory), true);
        try {
            assertEquals(LATENCY, watch.detectionDelay());
            fake.createFailure = new IOException("refused");
            watch.registrar().register(root, service);
            assertEquals(FALLBACK, watch.detectionDelay());
        } finally {
            watch.service().close();
        }
    }

    @Test
    @DisabledOnOs(OS.MAC)
    void whereFsEventsCannotBeLoadedTheLauncherPolls() throws Exception {
        // JNA is on the test classpath, CoreServices is not on this platform
        DevWatchService watch = DevWatchService.create(true, false, true);
        try {
            assertInstanceOf(PollingWatchService.class, watch.service());
        } finally {
            watch.service().close();
        }
    }

    private static void register(FakeFsEvents fake, Path root) throws IOException {
        FsEventsWatchService service = new FsEventsWatchService(fake, DevWatchService.MAC_LATENCY_SECONDS, FALLBACK);
        service.register(root);
        assertTrue(fake.runLoopThread.isAlive());
    }
}
