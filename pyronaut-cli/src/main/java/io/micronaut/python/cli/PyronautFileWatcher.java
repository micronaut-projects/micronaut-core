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
import io.micronaut.core.naming.Described;
import io.micronaut.python.cli.util.FileUtils;
import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.micronaut.python.cli.util.FileUtils.recurseDelete;
import static java.nio.file.Files.createDirectories;

/**
 * File watcher for Pyronaut applications that recompiles and restarts
 * the application when source files change.
 */
public class PyronautFileWatcher implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PyronautFileWatcher.class);

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;

    private final Path sourceDirectory;
    private final List<File> annotationProcessorPath;
    private final List<File> compileClassPath;
    private final Path outputDirectory;
    private final String[] parameters;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean starting = new AtomicBoolean(false);

    public PyronautFileWatcher(Path sourceDirectory,
                               List<File> annotationProcessorPath,
                               List<File> compileClassPath,
                               String[] parameters) {
        this.sourceDirectory = sourceDirectory.toAbsolutePath();
        this.outputDirectory = FileUtils.resolveOutputDirectory(sourceDirectory);
        this.annotationProcessorPath = annotationProcessorPath;
        this.compileClassPath = compileClassPath;
        this.parameters = parameters;
    }

    private static void async(Runnable action) {
        new Thread(action).start();
    }

    @Override
    public void run() {
        ApplicationContext currentContext = null;
        try {
            // Initial compilation and start
            if (compile() != SUCCESS) {
                System.err.println("Initial compilation failed");
                return;
            }
            currentContext = startApplication();

            // Set up file watching
            var watchService = FileSystems.getDefault().newWatchService();
            registerAll(sourceDirectory, watchService);

            System.out.println("Watching for changes in " + sourceDirectory);
            var outputPath = outputDirectory.toAbsolutePath();
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

                    var changed = ((Path) key.watchable()).resolve((Path) event.context());
                    if (Files.isHidden(changed) ||
                        changed.toAbsolutePath().startsWith(outputPath)) {
                        continue;
                    }
                    System.out.println(
                        "File changed: " + changed.toAbsolutePath() + " (" + kind + ")");

                    hasChanges = true;
                }

                if (hasChanges) {
                    if (starting.compareAndSet(false, true)) {
                        // TODO: ideally we should keep the existing app alive until
                        // we know that all files are compiled properly. This is difficult
                        // to implement because the generated classes contain an absolute
                        // path to where the files are generated, which makes it impossible
                        // to use a different directory for compilation and runtime.
                        stopApplication(currentContext);
                        System.out.println("Changes detected, recompiling and restarting...");
                        if (compile() == SUCCESS) {
                            currentContext = startApplication();
                        } else {
                            System.err.println("Compilation failed, application not restarted");
                        }
                    }
                }

                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            stopApplication(currentContext);
        }
    }

    private void registerAll(Path start, WatchService watchService) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
                dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private int compile() throws IOException {
        var builder = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString());
        if (!annotationProcessorPath.isEmpty()) {
            builder.annotationProcessorPath(annotationProcessorPath);
        }
        if (!compileClassPath.isEmpty()) {
            builder.classpath(compileClassPath);
        }
        var compiler = builder.targetDir(classesDirectory().toFile()).build();

        try {
            recurseDelete(classesDirectory());
            createDirectories(classesDirectory());
            compiler.compile();
            return SUCCESS;
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            return ERROR;
        }
    }

    private Path classesDirectory() {
        return outputDirectory.resolve("classes");
    }

    /**
     * Reproduces what Micronaut.run() does, WITHOUT adding a shutdown hook
     */
    private ApplicationContext startApplication() {
        long start = System.nanoTime();
        ApplicationContext currentContext = null;
        try {
            var classLoader = new URLClassLoader(buildUrls(classesDirectory(), sourceDirectory));
            currentContext = ApplicationContext.builder()
                .args(parameters)
                .classLoader(classLoader)
                .start();
            currentContext.findBean(EmbeddedApplication.class).ifPresent(embeddedApplication -> {
                embeddedApplication.start();
                if (embeddedApplication instanceof Described described) {
                    if (LOGGER.isInfoEnabled()) {
                        long took = elapsedMillis(start);
                        String desc = described.getDescription();
                        LOGGER.info("Startup completed in {}ms. Server Running: {}", took, desc);
                    }
                } else {
                    if (embeddedApplication instanceof EmbeddedServer embeddedServer) {
                        if (LOGGER.isInfoEnabled()) {
                            long took = elapsedMillis(start);
                            Object uri;
                            try {
                                uri = embeddedServer.getContextURI();
                            } catch (UnsupportedOperationException e) {
                                uri = "<URI display not available: " + e.getMessage() + ">";
                            }
                            LOGGER.info("Startup completed in {}ms. Server Running: {}", took, uri);
                        }
                    } else {
                        if (LOGGER.isInfoEnabled()) {
                            long took = elapsedMillis(start);
                            LOGGER.info("Startup completed in {}ms.", took);
                        }
                    }
                }
            });
            System.out.println("Application started");
            starting.set(false);
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
        return currentContext;
    }

    private void stopApplication(ApplicationContext currentContext) {
        if (currentContext != null && currentContext.isRunning()) {
            try {
                currentContext.findBean(EmbeddedApplication.class).ifPresentOrElse(EmbeddedApplication::stop, currentContext::stop);
                System.out.println("Application stopped");
            } catch (Exception e) {
                System.err.println("Error stopping application: " + e.getMessage());
            } finally {
                ContextHolder.resetContext();
            }
        }
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
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
