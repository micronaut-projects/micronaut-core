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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle of the FSEvents streams, on any platform, against a fake FSEvents: every stream is created, scheduled,
 * started, stopped, invalidated and released on the run loop thread, outside the run loop, its callback kept until it
 * is released, and the comparisons fall back to the polling interval when a stream or the run loop fails.
 */
class FsEventStreamsTest {

    private static final int[] NO_FLAGS = {0};

    @TempDir
    Path base;

    @Test
    void streamsAreCreatedStartedStoppedAndReleasedOnTheRunLoopThreadOutsideTheRunLoop() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path other = Files.createDirectories(base.resolve("other"));
        FakeFsEvents fake = new FakeFsEvents();
        AtomicInteger failed = new AtomicInteger();
        FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, failed::incrementAndGet);
        streams.watch(root);
        // a tree watched already holds it
        streams.watch(nested);
        streams.watch(other);
        streams.close();
        // closing again does nothing
        streams.close();

        String realRoot = root.toRealPath().toString();
        String realOther = other.toRealPath().toString();
        assertEquals(List.of(
            "create " + realRoot, "schedule " + realRoot, "start " + realRoot,
            "create " + realOther, "schedule " + realOther, "start " + realOther,
            "stop " + realRoot, "invalidate " + realRoot, "release " + realRoot,
            "stop " + realOther, "invalidate " + realOther, "release " + realOther
        ), fake.calls);
        assertEquals(List.of(), fake.violations);
        for (FakeFsEvents.Stream stream : fake.streams) {
            assertTrue(stream.released);
            assertFalse(FsEventStreams.isLive(stream));
        }
        assertFalse(fake.runLoopThread.isAlive());
        // closed by its owner: not a failure
        assertEquals(0, failed.get());
        assertThrows(ClosedWatchServiceException.class, () -> streams.watch(root));
    }

    @Test
    void aStreamIsKeptLiveFromItsCreationUntilItsRelease() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { })) {
            streams.watch(root);
            FakeFsEvents.Stream stream = fake.streams.get(0);
            assertTrue(FsEventStreams.isLive(stream));
            assertFalse(stream.released);
            streams.close();
            assertFalse(FsEventStreams.isLive(stream));
        }
    }

    @Test
    void theCallbackHandsOverTheWatchedDirectoriesOfTheReportedPaths() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        String realRoot = root.toRealPath().toString();
        FakeFsEvents fake = new FakeFsEvents();
        LinkedBlockingQueue<Path> changed = new LinkedBlockingQueue<>();
        List<Thread> threads = new CopyOnWriteArrayList<>();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> {
            threads.add(Thread.currentThread());
            changed.add(path);
        }, () -> { })) {
            streams.watch(root);
            fake.callBack(events -> events.report(new String[] {realRoot + "/nested"}, NO_FLAGS));
            assertEquals(nested, changed.poll(5, TimeUnit.SECONDS));
            // the events under the tree must be looked for: the tree is compared as a whole
            fake.callBack(events -> events.report(new String[] {realRoot + "/nested"}, new int[] {0x00000001}));
            assertEquals(root, changed.poll(5, TimeUnit.SECONDS));
            fake.callBack(FsEventStreams.Events::rescan);
            assertEquals(root, changed.poll(5, TimeUnit.SECONDS));
        }
        // on the run loop thread, while the loop runs
        assertSame(fake.runLoopThread, threads.get(0));
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void eventsAreMappedFromTheRealPathsToTheWatchedOnes() {
        Path root = Path.of("/watched/root");
        Path realRoot = Path.of("/private/real/root");
        List<Path> changed = new CopyOnWriteArrayList<>();
        FsEventStreams.Events events = new FsEventStreams.Events(root, realRoot, changed::add);
        events.report(new String[] {
            "/private/real/root/a/b", "/private/real/root/./c/../d", "/elsewhere/x", "/private/real/root/e",
            "/private/real/root/f", "/private/real/root/g", "/private/real/root/h"
        }, new int[] {0, 0, 0, 0x00000002, 0x00000004, 0x00000020, 0x00000100});
        assertEquals(List.of(
            root.resolve("a/b"), root.resolve("d"), root, root, root, root, root.resolve("h")
        ), changed);
        changed.clear();
        events.rescan();
        assertEquals(List.of(root), changed);
    }

    @Test
    void closingWhileACallbackRunsWaitsForItThenReleasesTheStreams() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        CountDownLatch inCallback = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { });
        streams.watch(root);
        fake.callBack(events -> {
            inCallback.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            order.add("callback ended, released " + fake.streams.get(0).released);
        });
        assertTrue(inCallback.await(5, TimeUnit.SECONDS));
        Thread closer = new Thread(() -> {
            streams.close();
            order.add("closed, released " + fake.streams.get(0).released);
        });
        closer.start();
        // the closer waits for the run loop thread, which is in the callback
        closer.join(200);
        assertTrue(closer.isAlive());
        release.countDown();
        closer.join(5000);
        assertFalse(closer.isAlive());
        assertEquals(List.of("callback ended, released false", "closed, released true"), order);
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void closingFromACallbackStopsTheRunLoopWithoutWaitingForItself() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        AtomicInteger failed = new AtomicInteger();
        FsEventStreams[] holder = new FsEventStreams[1];
        holder[0] = new FsEventStreams(fake, 0.05, path -> holder[0].close(), failed::incrementAndGet);
        holder[0].watch(root);
        fake.callBack(FsEventStreams.Events::rescan);
        fake.runLoopThread.join(5000);
        assertFalse(fake.runLoopThread.isAlive());
        assertTrue(fake.streams.get(0).released);
        assertEquals(0, failed.get());
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aStreamThatCannotBeCreatedIsNotWatchedAndTheOthersStillAre() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        Path other = Files.createDirectories(base.resolve("other"));
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { })) {
            fake.createFailure = new IOException("refused");
            IOException e = assertThrows(IOException.class, () -> streams.watch(root));
            assertTrue(e.getMessage().contains("refused"), e.getMessage());
            assertTrue(fake.streams.isEmpty());

            fake.createFailure = null;
            streams.watch(other);
            // the tree that failed was not registered: it is tried again
            streams.watch(root);
            assertEquals(2, fake.streams.size());
        }
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aStreamThatDoesNotStartIsReleasedWithoutBeingStopped() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        String realRoot = root.toRealPath().toString();
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { })) {
            fake.startFails = true;
            IOException e = assertThrows(IOException.class, () -> streams.watch(root));
            assertTrue(e.getMessage().contains("could not start"), e.getMessage());
            FakeFsEvents.Stream stream = fake.streams.get(0);
            assertTrue(stream.released);
            assertFalse(FsEventStreams.isLive(stream));
            assertEquals(List.of(
                "create " + realRoot, "schedule " + realRoot, "start " + realRoot, "invalidate " + realRoot, "release " + realRoot
            ), fake.calls);
        }
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aStreamThatCannotBeScheduledIsReleased() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { })) {
            fake.scheduleFailure = new IllegalStateException("no run loop");
            IOException e = assertThrows(IOException.class, () -> streams.watch(root));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertTrue(fake.streams.get(0).released);
            assertFalse(FsEventStreams.isLive(fake.streams.get(0)));
        }
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aWatchedDirectoryThatDoesNotExistIsRefused() throws Exception {
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { })) {
            assertThrows(IOException.class, () -> streams.watch(base.resolve("missing")));
            assertTrue(fake.calls.isEmpty());
        }
    }

    @Test
    void aRunLoopThatCannotStartFailsTheConstructor() {
        FakeFsEvents fake = new FakeFsEvents();
        fake.runLoopLinkageFailure = new UnsatisfiedLinkError("no CoreServices");
        AtomicInteger failed = new AtomicInteger();
        IOException e = assertThrows(IOException.class, () -> new FsEventStreams(fake, 0.05, path -> { }, failed::incrementAndGet));
        assertTrue(e.getMessage().contains("no CoreServices"), e.getMessage());
        // closed by the constructor, before the run loop ended
        assertEquals(0, failed.get());

        FakeFsEvents runtime = new FakeFsEvents();
        runtime.runLoopFailure = new IllegalStateException("no run loop");
        assertThrows(IOException.class, () -> new FsEventStreams(runtime, 0.05, path -> { }, () -> { }));
    }

    @Test
    void aRunLoopThatEndsReleasesTheStreamsAndReportsTheFailure() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        CountDownLatch failed = new CountDownLatch(1);
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, failed::countDown)) {
            fake.runFailure = new IllegalStateException("the run loop died");
            streams.watch(root);
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            fake.runLoopThread.join(5000);
            FakeFsEvents.Stream stream = fake.streams.get(0);
            assertTrue(stream.stopped);
            assertTrue(stream.released);
            assertFalse(FsEventStreams.isLive(stream));
            IOException e = assertThrows(IOException.class, () -> streams.watch(base));
            assertTrue(e.getMessage().contains("ended"), e.getMessage());
        }
        assertEquals(List.of(), fake.violations);
    }

    @Test
    void aWatchInterruptedWhileWaitingForTheRunLoopThreadFails() throws Exception {
        Path root = Files.createDirectories(base.resolve("root"));
        FakeFsEvents fake = new FakeFsEvents();
        try (FsEventStreams streams = new FsEventStreams(fake, 0.05, path -> { }, () -> { })) {
            Thread.currentThread().interrupt();
            IOException e = assertThrows(IOException.class, () -> streams.watch(root));
            assertInstanceOf(InterruptedException.class, e.getCause());
            assertTrue(Thread.interrupted());
        }
        assertEquals(List.of(), fake.violations);
    }
}
