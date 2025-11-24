/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.python.ContextHolder;
import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.runtime.Micronaut;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File watcher for Pyronaut applications that recompiles and restarts
 * the application when source files change.
 */
public class PyronautFileWatcher implements Runnable {

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;

    private final File sourceDirectory;
    private final Path outputDirectory;
    private final String[] parameters;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile ApplicationContext currentContext;

    public PyronautFileWatcher(File sourceDirectory, Path outputDirectory, String[] parameters) {
        this.sourceDirectory = sourceDirectory;
        this.outputDirectory = outputDirectory;
        this.parameters = parameters;
    }

    @Override
    public void run() {
        try {
            // Initial compilation and start
            if (compile() != SUCCESS) {
                System.err.println("Initial compilation failed");
                return;
            }
            startApplication();

            // Set up file watching
            var watchService = FileSystems.getDefault().newWatchService();
            registerAll(sourceDirectory.toPath(), watchService);

            System.out.println("Watching for changes in " + sourceDirectory.getAbsolutePath());

            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    break;
                }

                var hasChanges = false;
                for (var event : key.pollEvents()) {
                    var kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    var changed = (Path) event.context();
                    System.out.println("File changed: " + changed + " (" + kind + ")");

                    hasChanges = true;
                }

                if (hasChanges) {
                    System.out.println("Changes detected, recompiling and restarting...");
                    if (compile() == SUCCESS) {
                        stopApplication();
                        startApplication();
                    } else {
                        System.err.println("Compilation failed, application not restarted");
                    }
                }

                var valid = key.reset();
                if (!valid) {
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error setting up file watcher: " + e.getMessage());
        } finally {
            stopApplication();
        }
    }

    private void registerAll(Path start, WatchService watchService) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private int compile() {
        var compiler = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.getAbsolutePath())
            .targetDir(outputDirectory.toFile())
            .build();

        try {
            compiler.compile();
            return SUCCESS;
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            return ERROR;
        }
    }

    private void startApplication() {
        try {
            var classLoader =
                new URLClassLoader(buildUrls(outputDirectory, sourceDirectory.toPath()));
            currentContext = Micronaut.build(parameters)
                .args(parameters)
                .properties(Map.of("micronaut.lifecycle.graceful-shutdown.enabled", "true"))
                .classLoader(classLoader)
                .start();
            System.out.println("Application started");
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopApplication() {
        if (currentContext != null && currentContext.isRunning()) {
            try {
                while (currentContext.isRunning()) {
                    System.out.println(".");
                    Thread.sleep(10);
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println("Application stopped");
            } catch (Exception e) {
                System.err.println("Error stopping application: " + e.getMessage());
            } finally {
                currentContext = null;
                ContextHolder.resetContext();
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    private static URL[] buildUrls(Path... paths) throws MalformedURLException {
        var result = new URL[paths.length];
        for (var i = SUCCESS; i < paths.length; i++) {
            result[i] = paths[i].toUri().toURL();
        }
        return result;
    }
}
