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
package io.micronaut.scheduling.io.watch.osx

import io.methvin.watchservice.MacOSXListeningWatchService
import io.micronaut.scheduling.io.watch.DirectoryWatcher
import io.micronaut.scheduling.io.watch.FileChangeBatch
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

class NativeWatchRegistrarSpec extends Specification {

    @TempDir
    Path root

    void "a directory under a registered one is given that one's key, which is kept until the directory is deleted"() {
        given: "a registrar over a fake native registration"
        List<Path> registered = []
        NativeWatchRegistrar registrar = new NativeWatchRegistrar({ Path directory, WatchService service ->
            registered.add(directory)
            new FakeKey(directory)
        } as DirectoryWatcher.WatchKeyRegistrar)
        Path sub = root.resolve("sub")

        when:
        WatchKey rootKey = registrar.register(root, null)
        WatchKey subKey = registrar.register(sub, null)

        then: "the directory below shares the root's key, whose stream reports its changes"
        subKey.is(rootKey)
        registered == [root]

        when: "the watcher has no directory left for the key, which goes on reporting changes"
        registrar.cancel(rootKey)
        ((FakeKey) rootKey).events.add(Stub(WatchEvent))

        then: "the key is kept, and answers a later registration of the root without what changed meanwhile"
        rootKey.valid
        registrar.register(root, null).is(rootKey)
        ((FakeKey) rootKey).events.isEmpty()
        registered == [root]

        when: "a key in use reports a change while another directory under it is registered"
        ((FakeKey) rootKey).events.add(Stub(WatchEvent))
        registrar.register(sub, null)

        then: "the change stays for the watcher to drain"
        ((FakeKey) rootKey).events.size() == 1

        when: "the root is deleted, so the service cancelled its key"
        rootKey.cancel()
        WatchKey renewed = registrar.register(root, null)

        then: "it is registered natively again"
        !renewed.is(rootKey)
        registered == [root, root]

        when: "the watcher closes"
        registrar.close()

        then: "the keys kept are cancelled, so that the service, which stays open, queues nothing more for them"
        !renewed.valid
    }

    void "closing the watcher cancels the keys the registrar keeps"() {
        given:
        FakeKey key = new FakeKey(root)
        DirectoryWatcher watcher = DirectoryWatcher.builder(new FakeService())
            .registrar(new NativeWatchRegistrar({ Path directory, WatchService service -> key } as DirectoryWatcher.WatchKeyRegistrar))
            .closeWatchServiceOnClose(false)
            .build()
        def registration = watcher.directory(root).watch({})

        when: "the registration closes while the watcher runs"
        registration.close()

        then: "the key is kept"
        key.valid

        when: "a new registration is made and the watcher closes"
        watcher.directory(root).watch({})
        watcher.close()

        then:
        !key.valid
    }

    @Requires({ System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") })
    void "a registration under another one keeps receiving changes once that one closes, and the root can be watched again"() {
        given: "a watcher over the native service, and registrations of a directory and of a subdirectory"
        MacOSXListeningWatchService service = new MacOSXListeningWatchService()
        DirectoryWatcher watcher = DirectoryWatcher.builder(service)
            .registrar(new NativeWatchRegistrar())
            .closeWatchServiceOnClose(false)
            .quietPeriod(Duration.ofMillis(100))
            .build()
            .start()
        Path real = root.toRealPath()
        Path sub = Files.createDirectories(real.resolve("sub"))
        List<FileChangeBatch> parent = new CopyOnWriteArrayList<>()
        List<FileChangeBatch> child = new CopyOnWriteArrayList<>()
        def parentRegistration = watcher.directory(real).watch(parent::add)
        def childRegistration = watcher.directory(sub).watch(child::add)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.1)

        when:
        Files.writeString(sub.resolve("first.txt"), "x")

        then:
        conditions.eventually {
            assert child.any { it.paths().contains(sub.resolve("first.txt")) }
            assert parent.any { it.paths().contains(sub.resolve("first.txt")) }
        }

        when: "the registration of the directory closes"
        parentRegistration.close()
        Files.writeString(sub.resolve("second.txt"), "x")

        then: "the one of the subdirectory still receives changes"
        childRegistration.active
        conditions.eventually {
            assert child.any { it.paths().contains(sub.resolve("second.txt")) }
        }

        when: "both are closed and the directory is watched again"
        childRegistration.close()
        List<FileChangeBatch> again = new CopyOnWriteArrayList<>()
        def againRegistration = watcher.directory(real).watch(again::add)
        Files.writeString(real.resolve("third.txt"), "x")

        then: "it receives changes too"
        conditions.eventually {
            assert again.any { it.paths().contains(real.resolve("third.txt")) }
        }

        cleanup:
        againRegistration?.close()
        watcher?.close()
    }

    static class FakeService implements WatchService {
        @Override
        void close() {
        }

        @Override
        WatchKey poll() {
            return null
        }

        @Override
        WatchKey poll(long timeout, java.util.concurrent.TimeUnit unit) {
            return null
        }

        @Override
        WatchKey take() {
            throw new UnsupportedOperationException()
        }
    }

    static class FakeKey implements WatchKey {
        final Path directory
        final List<WatchEvent<?>> events = new CopyOnWriteArrayList<>()
        volatile boolean valid = true

        FakeKey(Path directory) {
            this.directory = directory
        }

        @Override
        boolean isValid() {
            return valid
        }

        @Override
        List<WatchEvent<?>> pollEvents() {
            List<WatchEvent<?>> polled = List.copyOf(events)
            events.clear()
            return polled
        }

        @Override
        boolean reset() {
            return valid
        }

        @Override
        void cancel() {
            valid = false
        }

        @Override
        Watchable watchable() {
            return directory
        }
    }
}
