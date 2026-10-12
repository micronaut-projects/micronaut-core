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

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileChangeBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How long an edit takes to reach the development runtime as a batch, with the watch service the launcher uses on
 * this platform, and that an editor's save of several files and a checkout of many land in one batch. The delays are
 * printed, prefixed with {@code [reload-latency]}, as the benchmark of the batching.
 */
class ReloadLatencyTest {

    private static final int EDITS = 8;
    // longer than any window of the batching: each edit starts from a quiet watcher, as one typed by hand does
    private static final long BETWEEN_EDITS_MILLIS = 1000;

    @TempDir
    Path project;

    @Test
    void anEditReachesTheWatcherSoon() throws Exception {
        measureWatcher(DevWatchService.create());
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            // without micronaut-runtime-osx, or in a native image
            measureWatcher(DevWatchService.polling());
        }
    }

    private void measureWatcher(DevWatchService watch) throws Exception {
        Path root = Files.createDirectories(project.resolve("watched-" + watch.service().getClass().getSimpleName()));
        Path file = Files.writeString(root.resolve("index.html"), "0");
        LinkedBlockingQueue<FileChangeBatch> batches = new LinkedBlockingQueue<>();
        DirectoryWatcher watcher = DirectoryWatcher.builder(watch.service())
            .registrar(watch.registrar())
            .closeWatchServiceOnClose(watch.closeOnClose())
            .build()
            .start();
        try {
            watcher.directory(root).watch(batches::add);
            settle(batches);
            List<Long> delays = new ArrayList<>();
            for (int i = 1; i <= EDITS; i++) {
                long start = System.nanoTime();
                Files.writeString(file, Integer.toString(i));
                FileChangeBatch batch = batches.poll(10, TimeUnit.SECONDS);
                assertNotNull(batch, "the edit " + i + " was not delivered");
                delays.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                settle(batches);
            }
            report("watcher " + watch.service().getClass().getSimpleName(), delays);
        } finally {
            watcher.close();
        }
    }

    @Test
    void anEditReachesTheRuntimeSoon() throws Exception {
        Path statics = Files.createDirectories(project.resolve("src/main/resources/public"));
        Path file = Files.writeString(statics.resolve("index.html"), "0");
        DevRuntime runtime = runtime();
        try {
            runtime.start(new String[0]);
            awaitQuiet(runtime);
            List<Long> delays = new ArrayList<>();
            for (int i = 1; i <= EDITS; i++) {
                long before = runtime.batchesHandled();
                long start = System.nanoTime();
                Files.writeString(file, Integer.toString(i));
                awaitBatch(runtime, before);
                delays.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                awaitQuiet(runtime);
                assertEquals(before + 1, runtime.batchesHandled(), "one edit, one batch");
            }
            report("edit to batch", delays);
        } finally {
            runtime.close();
        }
    }

    @Test
    void aSaveOfSeveralFilesLandsInOneBatch() throws Exception {
        Path statics = Files.createDirectories(project.resolve("src/main/resources/public"));
        Path views = Files.createDirectories(project.resolve("src/main/resources/views"));
        Files.writeString(statics.resolve("app.css"), "0");
        Files.writeString(views.resolve("home.html"), "0");
        DevRuntime runtime = runtime();
        try {
            runtime.start(new String[0]);
            awaitQuiet(runtime);
            for (int round = 1; round <= 3; round++) {
                long before = runtime.batchesHandled();
                // a save-all: files of two roots, one written in place, one through a temporary file renamed over it
                Files.writeString(statics.resolve("app.css"), "body { order: " + round + " }");
                Thread.sleep(5);
                Path temporary = Files.writeString(views.resolve("home.html~"), "<p>" + round + "</p>");
                Files.move(temporary, views.resolve("home.html"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Thread.sleep(5);
                Files.writeString(statics.resolve("new" + round + ".js"), "let x = " + round + ";");
                awaitBatch(runtime, before);
                awaitQuiet(runtime);
                assertEquals(before + 1, runtime.batchesHandled(), "the save of round " + round + " was split");
            }
        } finally {
            runtime.close();
        }
    }

    @Test
    void aCheckoutOfManyFilesLandsInOneBatch() throws Exception {
        Path statics = Files.createDirectories(project.resolve("src/main/resources/public"));
        Files.writeString(statics.resolve("index.html"), "0");
        DevRuntime runtime = runtime();
        try {
            runtime.start(new String[0]);
            awaitQuiet(runtime);
            long before = runtime.batchesHandled();
            // a checkout: files written one after the other, some in directories it creates, for about a third of a second
            for (int i = 0; i < 40; i++) {
                Path directory = Files.createDirectories(statics.resolve("pages/section" + (i / 10)));
                Files.writeString(directory.resolve("page" + i + ".html"), "<p>" + i + "</p>");
                Thread.sleep(8);
            }
            awaitBatch(runtime, before);
            awaitQuiet(runtime);
            assertEquals(before + 1, runtime.batchesHandled(), "the checkout was split");
        } finally {
            runtime.close();
        }
    }

    private DevRuntime runtime() throws IOException {
        Files.createDirectories(project.resolve("build/classes"));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.static=src/main/resources/public
            micronaut.dev.resources.views=src/main/resources/views
            micronaut.dev.livereload.port=0
            """);
        DevManifest manifest = DevManifest.load(manifestFile);
        DevClassLoader loader = new DevClassLoader(ReloadLatencyTest.class.getClassLoader(), manifest.reloadableRoots(), project.resolve("build/generations"));
        return new DevRuntime(manifest, loader, (classLoader, mainClass, args) -> ApplicationContext.builder()
            .classLoader(classLoader)
            .properties(Map.of("spec.name", "ReloadLatencyTest"))
            .start(), Map.of());
    }

    private static void awaitBatch(DevRuntime runtime, long before) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (runtime.batchesHandled() == before) {
            assertTrue(System.nanoTime() < deadline, "no batch was handled");
            Thread.sleep(1);
        }
    }

    private static void awaitQuiet(DevRuntime runtime) throws InterruptedException {
        // whatever a late report brings is handled within this
        Thread.sleep(BETWEEN_EDITS_MILLIS);
        runtime.whenReady().join();
    }

    private static void settle(LinkedBlockingQueue<FileChangeBatch> batches) throws InterruptedException {
        Thread.sleep(BETWEEN_EDITS_MILLIS);
        batches.clear();
    }

    private static void report(String what, List<Long> delays) {
        List<Long> sorted = new ArrayList<>(delays);
        sorted.sort(null);
        long median = sorted.get(sorted.size() / 2);
        System.out.printf(Locale.ROOT, "[reload-latency] %s on %s: median %d ms, min %d ms, max %d ms, all %s%n",
            what, System.getProperty("os.name"), median, sorted.get(0), sorted.get(sorted.size() - 1), delays);
    }
}
