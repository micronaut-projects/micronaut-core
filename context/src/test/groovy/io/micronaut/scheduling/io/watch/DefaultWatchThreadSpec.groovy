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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.scheduling.io.watch.event.FileChangedEvent
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

class DefaultWatchThreadSpec extends Specification {

    @TempDir
    Path root

    void "the watch thread publishes absolute paths through the FileWatcher of the context"() {
        given:
        ApplicationContext context = ApplicationContext.run(
            (FileWatchConfiguration.PATHS): root.toString(),
            (FileWatchConfiguration.ENABLED): true,
            'micronaut.io.watch.check-interval': '50ms',
            'micronaut.io.watch.quiet-period': '100ms',
            'spec.name': 'DefaultWatchThreadSpec'
        )
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.1)
        Path other = Files.createDirectories(root.resolveSibling(root.fileName.toString() + "-other"))

        expect:
        context.getBean(FileWatchConfiguration).quietPeriod.toMillis() == 100
        context.getBean(FileWatcher) instanceof DefaultFileWatcher
        context.getBean(DefaultWatchThread).running
        context.getBean(FileWatcher).isWatching(root)
        !context.getBean(FileWatcher).isWatching(other)

        when: "a component registers a directory of its own"
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        def registration = context.getBean(FileWatcher).directory(other).watch(batches::add)
        Files.writeString(root.resolve("changed.txt"), "x")
        Files.writeString(other.resolve("mine.txt"), "x")
        Listener listener = context.getBean(Listener)

        then:
        conditions.eventually {
            assert listener.events.any { it.path == root.resolve("changed.txt").toAbsolutePath().normalize() }
            assert batches.any { it.paths().contains(other.resolve("mine.txt").toAbsolutePath().normalize()) }
        }
        listener.events.every { it.path.isAbsolute() }
        !listener.events.any { it.path.fileName.toString() == "mine.txt" }

        cleanup:
        registration.close()
        context.close()
        other.toFile().deleteDir()
    }

    void "the FileWatcher is a bean without the watch paths, and starts its thread on the first registration"() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'DefaultWatchThreadSpec')

        expect:
        !context.containsBean(DefaultWatchThread)
        context.containsBean(FileWatcher)

        when:
        FileWatcher fileWatcher = context.getBean(FileWatcher)

        then: "no watcher, so no thread, before a registration"
        ((DefaultFileWatcher) fileWatcher).@watcher == null
        !fileWatcher.isWatching(root)

        when:
        List<FileChangeBatch> batches = new CopyOnWriteArrayList<>()
        def registration = fileWatcher.directory(root).watch(batches::add)
        Files.writeString(root.resolve("lazy.txt"), "x")

        then:
        ((DefaultFileWatcher) fileWatcher).@watcher.running
        fileWatcher.isWatching(root)
        new PollingConditions(timeout: 30, delay: 0.1).eventually {
            assert batches.any { it.paths().contains(root.resolve("lazy.txt").toAbsolutePath().normalize()) }
        }

        when:
        context.close()

        then: "the context closes the watcher"
        !registration.active
        ((DefaultFileWatcher) fileWatcher).@watcher == null
    }

    void "switching the watch off removes the FileWatcher"() {
        given:
        ApplicationContext context = ApplicationContext.run((FileWatchConfiguration.ENABLED): false)

        expect:
        !context.containsBean(FileWatcher)

        cleanup:
        context.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'DefaultWatchThreadSpec')
    static class Listener implements ApplicationEventListener<FileChangedEvent> {
        List<FileChangedEvent> events = new CopyOnWriteArrayList<>()

        @Override
        void onApplicationEvent(FileChangedEvent event) {
            events.add(event)
        }
    }
}
