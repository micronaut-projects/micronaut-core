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

import io.micronaut.scheduling.io.watch.event.WatchEventType
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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
        def registration = watcher.watch(root, batches::add)

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
        watcher.watch(root, batches::add)

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
        watcher.watch(root, WatchOptions.DEFAULT.excluding("**/*.log"), batches::add)
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
        watcher.watch(root, batches::add)

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
        watcher.watch(root, WatchOptions.DEFAULT.excluding("build"), batches::add)

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
        watcher.watch(root, WatchOptions.DEFAULT.excluding("build/**", "**/*.tmp"), batches::add)

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
        watcher.watch(root, WatchOptions.DEFAULT.including("**/*.html", "*.html"), batches::add)

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
        def r1 = watcher.watch(root, first::add)
        def r2 = watcher.watch(root, WatchOptions.nonRecursive(), second::add)

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
        watcher.watch(root, WatchOptions.DEFAULT.excluding("build"), strict::add)
        watcher.watch(root, loose::add)

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
        watcher.watch(root) { throw new IllegalStateException("boom") }
        watcher.watch(root, good::add)

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

    void "watching a missing directory is rejected"() {
        when:
        watcher.watch(root.resolve("missing"), {})

        then:
        thrown(IllegalArgumentException)
    }

    void "closing the watcher deactivates registrations and stops the thread"() {
        given:
        def registration = watcher.watch(root, {})

        when:
        watcher.close()

        then:
        !registration.active
        !watcher.running
        watcher.watchedDirectories().isEmpty()

        when:
        watcher.watch(root, {})

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
}
