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
package io.micronaut.scheduling.io.watch

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.scheduling.io.watch.event.WatchEventType
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.time.Duration
import java.nio.file.ClosedWatchServiceException
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DirectoryWatcherSpec extends Specification {

    @TempDir
    Path root

    @AutoCleanup
    DirectoryWatcher watcher

    PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.1)

    def setup() {
        watcher = DirectoryWatcher.builder(FileSystems.default.newWatchService())
            .registrar(DirectoryWatcherSpec.&registerSensitive)
            .checkInterval(Duration.ofMillis(50))
            .quietPeriod(Duration.ofMillis(100))
            .build()
            .start()
    }

    void "a change is reported with an absolute path in one batch"() {
        given:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        def registration = watcher.directory(root).watch(batches::add)

        when:
        Files.writeString(root.resolve("a.txt"), "hello")

        then:
        conditions.eventually {
            assert !batches.isEmpty()
        }
        def batch = batches.first()
        batch.root() == root.toAbsolutePath().normalize()
        batch.changes().every { it.path().isAbsolute() }
        batch.paths().contains(root.resolve("a.txt").toAbsolutePath().normalize())
        batch.contains(WatchEventType.CREATE)
        registration.root() == root.toAbsolutePath().normalize()
        registration.active
        watcher.isWatching(root.resolve("a.txt"))

        cleanup:
        registration.close()
    }

    void "events of one save are coalesced into one change"() {
        given:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).watch(batches::add)

        when: "a file is created and written several times within the quiet period"
        Path file = root.resolve("b.txt")
        Files.writeString(file, "1")
        Files.writeString(file, "12")
        Files.writeString(file, "123")

        then:
        conditions.eventually {
            assert !batches.isEmpty()
        }
        batches.first().changes().count { it.path().fileName.toString() == "b.txt" } == 1
        batches.first().changes().find { it.path().fileName.toString() == "b.txt" }.type() == WatchEventType.CREATE
    }

    void "a stream of excluded events cannot postpone a batch for ever"() {
        given:
        Files.createDirectories(root.resolve("src"))
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).exclude("**/*.log").watch(batches::add)
        Path log = root.resolve("src/noise.log")
        Path source = root.resolve("src/Main.java")
        AtomicBoolean writing = new AtomicBoolean(true)
        Thread noise = new Thread({
            int i = 0
            while (writing.get()) {
                Files.writeString(log, "line " + (i++))
                Thread.sleep(20)
            }
        })

        when: "an excluded file is written more often than the quiet period while a source file changes once"
        noise.start()
        Thread.sleep(200)
        Files.writeString(source, "class Main {}")

        then: "the source change is still delivered"
        conditions.eventually {
            assert batches.any { it.paths().contains(source.toAbsolutePath().normalize()) }
        }
        !batches.any { batch -> batch.paths().any { it.toString().endsWith(".log") } }

        cleanup:
        writing.set(false)
        noise.join()
    }

    void "a directory created after the registration is watched too"() {
        given:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).watch(batches::add)

        when:
        Path sub = Files.createDirectories(root.resolve("nested/deeper"))

        then:
        conditions.eventually {
            assert watcher.watchedDirectories().contains(sub.toAbsolutePath().normalize())
        }

        when:
        batches.clear()
        Files.writeString(sub.resolve("c.txt"), "deep")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(sub.resolve("c.txt").toAbsolutePath().normalize()) }
        }
    }

    void "hidden and excluded directories created later are not watched"() {
        given:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).exclude("build/**").watch(batches::add)

        when:
        Path hidden = Files.createDirectories(root.resolve(".git"))
        Path build = Files.createDirectories(root.resolve("build"))
        Path src = Files.createDirectories(root.resolve("src"))

        then:
        conditions.eventually {
            assert watcher.watchedDirectories().contains(src.toAbsolutePath().normalize())
        }
        !watcher.watchedDirectories().contains(hidden.toAbsolutePath().normalize())
        !watcher.watchedDirectories().contains(build.toAbsolutePath().normalize())

        when:
        batches.clear()
        Files.writeString(hidden.resolve("HEAD"), "x")
        Files.writeString(build.resolve("out.class"), "x")
        Files.writeString(src.resolve("Main.java"), "x")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(src.resolve("Main.java").toAbsolutePath().normalize()) }
        }
        !batches.any { batch -> batch.paths().any { it.toString().endsWith("HEAD") || it.toString().endsWith("out.class") } }
    }

    void "exclude patterns skip directories and files"() {
        given:
        Files.createDirectories(root.resolve("build"))
        Files.createDirectories(root.resolve("src"))
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).exclude("build/**").exclude("**/*.tmp").watch(batches::add)

        expect:
        !watcher.watchedDirectories().contains(root.resolve("build").toAbsolutePath().normalize())

        when:
        Files.writeString(root.resolve("build/out.class"), "x")
        Files.writeString(root.resolve("src/scratch.tmp"), "x")
        Files.writeString(root.resolve("src/Main.java"), "x")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(root.resolve("src/Main.java").toAbsolutePath().normalize()) }
        }
        !batches.any { batch -> batch.paths().any { it.toString().endsWith("out.class") || it.toString().endsWith(".tmp") } }
    }

    void "include patterns only report matching files"() {
        given:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).include("{**/,}*.html").watch(batches::add)

        when:
        Files.writeString(root.resolve("ignored.txt"), "x")
        Files.writeString(root.resolve("page.html"), "x")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(root.resolve("page.html").toAbsolutePath().normalize()) }
        }
        !batches.any { batch -> batch.paths().any { it.toString().endsWith("ignored.txt") } }
    }

    void "two registrations of the same directory share one watch key and release it together"() {
        given:
        List<FileChangeBatch> first = new CopyOnWriteArrayList<>()
        List<FileChangeBatch> second = new CopyOnWriteArrayList<>()
        def r1 = watcher.directory(root).watch(first::add)
        def r2 = watcher.directory(root).recursive(false).watch(second::add)

        expect:
        watcher.watchedDirectories().count { it == root.toAbsolutePath().normalize() } == 1

        when:
        Files.writeString(root.resolve("shared.txt"), "x")

        then:
        conditions.eventually {
            assert first.any { it.paths().contains(root.resolve("shared.txt").toAbsolutePath().normalize()) }
            assert second.any { it.paths().contains(root.resolve("shared.txt").toAbsolutePath().normalize()) }
        }

        when:
        r1.close()

        then:
        !r1.active
        watcher.watchedDirectories().contains(root.toAbsolutePath().normalize())
        watcher.isWatching(root)

        when:
        r2.close()

        then:
        !watcher.watchedDirectories().contains(root.toAbsolutePath().normalize())
        !watcher.isWatching(root)
    }

    void "an overlapping registration without exclusions does not leak excluded changes"() {
        given:
        Files.createDirectories(root.resolve("build"))
        Files.createDirectories(root.resolve("src"))
        List<FileChangeBatch> strict = new CopyOnWriteArrayList<>()
        List<FileChangeBatch> loose = new CopyOnWriteArrayList<>()
        watcher.directory(root).exclude("build/**").watch(strict::add)
        watcher.directory(root).watch(loose::add)

        when:
        Files.writeString(root.resolve("build/out.class"), "x")
        Files.writeString(root.resolve("src/Main.java"), "x")

        then:
        conditions.eventually {
            assert loose.any { it.paths().contains(root.resolve("build/out.class").toAbsolutePath().normalize()) }
            assert strict.any { it.paths().contains(root.resolve("src/Main.java").toAbsolutePath().normalize()) }
        }
        !strict.any { batch -> batch.paths().any { it.toString().endsWith("out.class") } }
    }

    void "a failing listener does not stop the watcher or other listeners"() {
        given:
        List<FileChangeBatch> good = new CopyOnWriteArrayList<>()
        watcher.directory(root).watch { throw new IllegalStateException("boom") }
        watcher.directory(root).watch(good::add)

        when:
        Files.writeString(root.resolve("first.txt"), "x")

        then:
        conditions.eventually {
            assert good.any { it.paths().contains(root.resolve("first.txt").toAbsolutePath().normalize()) }
        }

        when:
        good.clear()
        Files.writeString(root.resolve("second.txt"), "x")

        then:
        conditions.eventually {
            assert good.any { it.paths().contains(root.resolve("second.txt").toAbsolutePath().normalize()) }
        }
        watcher.running
    }

    void "an exclude of the direct children of a directory leaves the directories below them watched"() {
        given:
        Path sub = Files.createDirectories(root.resolve("build/sub"))
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).exclude("build/*").watch(batches::add)

        expect:
        watcher.watchedDirectories().contains(root.resolve("build").toAbsolutePath().normalize())
        watcher.watchedDirectories().contains(sub.toAbsolutePath().normalize())

        when:
        Files.writeString(root.resolve("build/direct.txt"), "x")
        Files.writeString(sub.resolve("deep.txt"), "x")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(sub.resolve("deep.txt").toAbsolutePath().normalize()) }
        }
        !batches.any { batch -> batch.paths().any { it.fileName.toString() == "direct.txt" } }
    }

    void "a file created and deleted within one quiet period is not reported"() {
        given:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).watch(batches::add)

        when:
        Path ephemeral = root.resolve("transient.txt")
        Files.writeString(ephemeral, "x")
        Files.delete(ephemeral)
        Files.writeString(root.resolve("marker.txt"), "x")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(root.resolve("marker.txt").toAbsolutePath().normalize()) }
        }
        !batches.any { batch -> batch.paths().contains(ephemeral.toAbsolutePath().normalize()) }
    }

    void "the merge rules of a change"() {
        given:
        Path path = root.resolve("x").toAbsolutePath()

        expect:
        new FileChange(path, first).merge(later)?.type() == merged

        where:
        first                  | later                  | merged
        WatchEventType.CREATE  | WatchEventType.MODIFY  | WatchEventType.CREATE
        WatchEventType.CREATE  | WatchEventType.DELETE  | null
        WatchEventType.MODIFY  | WatchEventType.DELETE  | WatchEventType.DELETE
        WatchEventType.MODIFY  | WatchEventType.MODIFY  | WatchEventType.MODIFY
        WatchEventType.DELETE  | WatchEventType.CREATE  | WatchEventType.MODIFY
        WatchEventType.DELETE  | WatchEventType.DELETE  | WatchEventType.DELETE
    }

    void "a request is snapshotted by each registration and may be reused"() {
        given:
        List<FileChangeBatch> all = new CopyOnWriteArrayList<>()
        List<FileChangeBatch> html = new CopyOnWriteArrayList<>()
        def request = watcher.directory(root)
        def r1 = request.watch(all::add)
        def r2 = request.include("*.html").watch(html::add)

        when:
        Files.writeString(root.resolve("a.txt"), "x")
        Files.writeString(root.resolve("b.html"), "x")

        then:
        conditions.eventually {
            assert all.any { it.paths().contains(root.resolve("a.txt").toAbsolutePath().normalize()) }
            assert all.any { it.paths().contains(root.resolve("b.html").toAbsolutePath().normalize()) }
            assert html.any { it.paths().contains(root.resolve("b.html").toAbsolutePath().normalize()) }
        }
        !html.any { batch -> batch.paths().any { it.fileName.toString() == "a.txt" } }

        cleanup:
        r1.close()
        r2.close()
    }

    void "while a listener's stage is pending its batches are held back and merged, and other registrations are delivered"() {
        given:
        List<FileChangeBatch> slow = new CopyOnWriteArrayList<>()
        List<FileChangeBatch> fast = new CopyOnWriteArrayList<>()
        List<CompletableFuture<Void>> stages = new CopyOnWriteArrayList<>()
        watcher.directory(root).watchAsync({ FileChangeBatch batch ->
            slow.add(batch)
            CompletableFuture<Void> stage = new CompletableFuture<>()
            stages.add(stage)
            stage
        })
        watcher.directory(root).watch(fast::add)

        when: "a first change starts the slow listener's work"
        Files.writeString(root.resolve("first.txt"), "x")

        then:
        conditions.eventually {
            assert slow.size() == 1
        }

        when: "more changes arrive, in separate quiet periods, while the work is under way"
        for (name in ["second.txt", "third.txt", "fourth.txt"]) {
            Files.writeString(root.resolve(name), "x")
            Path expected = root.resolve(name).toAbsolutePath().normalize()
            conditions.eventually {
                assert fast.any { it.paths().contains(expected) }
            }
        }
        Files.writeString(root.resolve("second.txt"), "changed again")
        Thread.sleep(500)

        then: "the slow listener received nothing more"
        slow.size() == 1

        when: "its work completes"
        stages[0].complete(null)

        then: "the held changes arrive merged in one batch"
        conditions.eventually {
            assert slow.size() == 2
        }
        def merged = slow[1]
        merged.paths().containsAll(["second.txt", "third.txt", "fourth.txt"].collect { root.resolve(it).toAbsolutePath().normalize() })
        merged.changes().find { it.path().fileName.toString() == "second.txt" }.type() == WatchEventType.CREATE

        cleanup:
        stages.each { it.complete(null) }
    }

    void "a stage that completes exceptionally is logged and the next batch is delivered"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(DirectoryWatcher)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        watcher.directory(root).watchAsync({ FileChangeBatch batch ->
            batches.add(batch)
            CompletableFuture.supplyAsync { throw new IllegalStateException("async boom") }
        })

        when:
        Files.writeString(root.resolve("one.txt"), "x")

        then:
        conditions.eventually {
            assert appender.list.any { it.level == Level.ERROR && it.throwableProxy?.message == "async boom" }
        }

        when:
        Files.writeString(root.resolve("two.txt"), "x")

        then:
        conditions.eventually {
            assert batches.any { it.paths().contains(root.resolve("two.txt").toAbsolutePath().normalize()) }
        }

        cleanup:
        logger.detachAppender(appender)
    }

    void "no listener is called once its registration is closed, and close waits for a call under way"() {
        given:
        CountDownLatch entered = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicInteger calls = new AtomicInteger()
        def blocking = watcher.directory(root).watch {
            calls.incrementAndGet()
            entered.countDown()
            release.await(30, TimeUnit.SECONDS)
        }
        CompletableFuture<Void> pending = new CompletableFuture<>()
        AtomicInteger asyncCalls = new AtomicInteger()
        def async = watcher.directory(root).watchAsync({ FileChangeBatch batch ->
            asyncCalls.incrementAndGet()
            pending
        })

        when: "a change reaches the blocking listener"
        Files.writeString(root.resolve("a.txt"), "x")

        then:
        entered.await(30, TimeUnit.SECONDS)

        when: "the registration is closed from another thread while the listener runs"
        Thread closer = new Thread({ blocking.close() })
        closer.start()
        Thread.sleep(300)

        then: "close waits for the call to return"
        closer.alive

        when:
        release.countDown()
        closer.join(30_000)

        then:
        !closer.alive
        !blocking.active

        when: "the asynchronous registration is closed while its stage is pending and changes are held for it"
        conditions.eventually {
            assert asyncCalls.get() == 1
        }
        Files.writeString(root.resolve("b.txt"), "x")
        Thread.sleep(500)
        async.close()
        pending.complete(null)
        Files.writeString(root.resolve("c.txt"), "x")
        Thread.sleep(800)

        then: "neither listener is called again"
        calls.get() == 1
        asyncCalls.get() == 1
    }

    void "changes recorded from a key that became invalid are delivered before its directory is released"() {
        given: "a controlled service and a registration of java sources"
        Path src = Files.createDirectories(root.resolve("src"))
        ControlledWatchService service = new ControlledWatchService()
        DirectoryWatcher controlled = controlledWatcher(service)
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        controlled.directory(root).include("**/*.java").watch(batches::add)
        Path deleted = src.resolve("Main.java").toAbsolutePath().normalize()

        when: "the source is deleted with its directory, so that the key cannot be reset"
        service.signal(src, false, event(StandardWatchEventKinds.ENTRY_DELETE, Path.of("Main.java")))

        then: "the deletion is still delivered, and the directory is released"
        conditions.eventually {
            assert batches.size() == 1
        }
        batches[0].changes() == [new FileChange(deleted, WatchEventType.DELETE)]
        !controlled.watchedDirectories().contains(src.toAbsolutePath().normalize())

        cleanup:
        controlled.close()
    }

    void "a registration made while a change is pending is not merged with what it never saw: #later"() {
        given: "a controlled service with a long quiet period and a first registration"
        ControlledWatchService service = new ControlledWatchService()
        DirectoryWatcher controlled = controlledWatcher(service)
        List<FileChangeBatch> before = new CopyOnWriteArrayList<>()
        List<FileChangeBatch> after = new CopyOnWriteArrayList<>()
        controlled.directory(root).watch(before::add)
        Path file = root.resolve("a.txt").toAbsolutePath().normalize()

        when: "a file is created, then a second registration is made, then the file changes again within the quiet period"
        service.signalAndWait(root, true, event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("a.txt")))
        controlled.directory(root).watch(after::add)
        service.signalAndWait(root, true, event(later, Path.of("a.txt")))

        then: "the second registration receives the later change as it happened"
        conditions.eventually {
            assert after.size() == 1
        }
        after[0].changes() == [new FileChange(file, WatchEventType.of(later))]

        and: "the first one receives the changes merged"
        Thread.sleep(300)
        before.collectMany { it.changes() } == (expectedBefore == null ? [] : [new FileChange(file, expectedBefore)])

        cleanup:
        controlled.close()

        where:
        later                               | expectedBefore
        StandardWatchEventKinds.ENTRY_DELETE | null
        StandardWatchEventKinds.ENTRY_MODIFY | WatchEventType.CREATE
    }

    void "a builder without a service creates and closes one of the default file system"() {
        given:
        DirectoryWatcher own = DirectoryWatcher.builder().build()

        expect:
        own.watchService != null

        when:
        own.close()
        own.watchService.poll()

        then:
        thrown(ClosedWatchServiceException)
    }

    void "a watch service the caller keeps is left open"() {
        given:
        WatchService service = FileSystems.default.newWatchService()
        DirectoryWatcher own = DirectoryWatcher.builder(service).closeWatchServiceOnClose(false).build()

        when:
        own.close()
        service.poll()

        then:
        notThrown(ClosedWatchServiceException)

        cleanup:
        service.close()
    }

    void "watching a missing directory is rejected"() {
        when: "the request is made: nothing is checked yet"
        def request = watcher.directory(root.resolve("missing"))

        then:
        notThrown(Exception)

        when: "its terminal operation registers"
        request.watch({})

        then:
        thrown(IllegalArgumentException)
    }

    void "closing the watcher deactivates registrations and stops the thread"() {
        given:
        def registration = watcher.directory(root).watch({})

        when:
        watcher.close()

        then:
        !registration.active
        !watcher.running
        watcher.watchedDirectories().isEmpty()

        when:
        watcher.directory(root).watch({})

        then:
        thrown(IllegalStateException)
    }

    /**
     * The JDK falls back to polling on platforms without native change notification, every ten seconds
     * by default. The high sensitivity keeps the specs quick there.
     */
    private static java.nio.file.WatchKey registerSensitive(Path dir, java.nio.file.WatchService service) {
        WatchEvent.Kind[] kinds = [StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY] as WatchEvent.Kind[]
        WatchEvent.Modifier high = null
        try {
            high = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier").getField("HIGH").get(null) as WatchEvent.Modifier
        } catch (Throwable ignored) {
        }
        return high == null ? dir.register(service, kinds) : dir.register(service, kinds, high)
    }

    private static DirectoryWatcher controlledWatcher(ControlledWatchService service) {
        return DirectoryWatcher.builder(service)
            .registrar({ Path dir, WatchService s -> ((ControlledWatchService) s).register(dir) } as DirectoryWatcher.WatchKeyRegistrar)
            .checkInterval(Duration.ofMillis(20))
            .quietPeriod(Duration.ofMillis(400))
            .build()
            .start()
    }

    private static WatchEvent<Path> event(WatchEvent.Kind<Path> kind, Path context) {
        return new WatchEvent<Path>() {
            @Override
            WatchEvent.Kind<Path> kind() {
                return kind
            }

            @Override
            int count() {
                return 1
            }

            @Override
            Path context() {
                return context
            }
        }
    }

    /**
     * A watch service whose keys are signalled by the spec, with the events it chooses.
     */
    static class ControlledWatchService implements WatchService {
        final LinkedBlockingQueue<ControlledKey> signalled = new LinkedBlockingQueue<>()
        final Map<Path, ControlledKey> keys = new ConcurrentHashMap<>()
        volatile boolean closed

        WatchKey register(Path dir) {
            return keys.computeIfAbsent(dir.toAbsolutePath().normalize(), { Path p -> new ControlledKey(p) })
        }

        void signal(Path dir, boolean resettable, WatchEvent<?>... events) {
            ControlledKey key = keys.get(dir.toAbsolutePath().normalize())
            assert key != null
            key.offer(resettable, events)
            signalled.add(key)
        }

        /**
         * Signals the key and waits until the watcher recorded its events, which it has once it reset the key.
         */
        void signalAndWait(Path dir, boolean resettable, WatchEvent<?>... events) {
            ControlledKey key = keys.get(dir.toAbsolutePath().normalize())
            int resets = key.resets.get()
            signal(dir, resettable, events)
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (key.resets.get() == resets) {
                assert System.nanoTime() < deadline
                Thread.sleep(5)
            }
        }

        @Override
        void close() {
            closed = true
        }

        @Override
        WatchKey poll() {
            if (closed) {
                throw new ClosedWatchServiceException()
            }
            return signalled.poll()
        }

        @Override
        WatchKey poll(long timeout, TimeUnit unit) {
            if (closed) {
                throw new ClosedWatchServiceException()
            }
            return signalled.poll(timeout, unit)
        }

        @Override
        WatchKey take() {
            return signalled.take()
        }
    }

    static class ControlledKey implements WatchKey {
        final Path dir
        final List<WatchEvent<?>> events = []
        final AtomicInteger resets = new AtomicInteger()
        volatile boolean resettable = true
        volatile boolean valid = true

        ControlledKey(Path dir) {
            this.dir = dir
        }

        synchronized void offer(boolean resettable, WatchEvent<?>... offered) {
            this.resettable = resettable
            events.addAll(offered)
        }

        @Override
        boolean isValid() {
            return valid
        }

        @Override
        synchronized List<WatchEvent<?>> pollEvents() {
            List<WatchEvent<?>> polled = List.copyOf(events)
            events.clear()
            return polled
        }

        @Override
        boolean reset() {
            if (!resettable) {
                valid = false
            }
            resets.incrementAndGet()
            return valid
        }

        @Override
        void cancel() {
            valid = false
        }

        @Override
        Watchable watchable() {
            return dir
        }
    }
}
