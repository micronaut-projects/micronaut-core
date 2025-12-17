/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.python.cli;

//import io.micronaut.context.ApplicationContext;
//import io.micronaut.core.naming.Described;

import io.micronaut.python.cli.util.FileUtils;
import io.micronaut.python.cli.util.MavenArtifact;
import io.micronaut.python.cli.util.PythonMavenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final PythonMavenRepository annotationProcessorRepo;
    private final PythonMavenRepository compileClassPathRepo;
    private final Path outputDirectory;
    private final String[] parameters;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final static List<String> WATCHED_DIRECTORIES = List.of("src", "config");

    public PyronautFileWatcher(Path sourceDirectory,
                               PythonMavenRepository annotationProcessorRepo,
                               PythonMavenRepository compileClassPathRepo,
                               String[] parameters) {
        this.sourceDirectory = sourceDirectory.toAbsolutePath();
        this.outputDirectory = FileUtils.resolveOutputDirectory(sourceDirectory);
        this.annotationProcessorRepo = annotationProcessorRepo;
        this.compileClassPathRepo = compileClassPathRepo;
        this.parameters = parameters;
    }

    @Override
    public void run() {
        ApplicationManager appManager = null;
        try {
            var truffleClassloader = createTruffleClassLoader(compileClassPathRepo);
            // Initial compilation and start
            if (compile(truffleClassloader) != SUCCESS) {
                System.err.println("Initial compilation failed");
                return;
            }
            var classpath = buildUrls(classesDirectory(), sourceDirectory.resolve("config"));
            var classLoader = new URLClassLoader(classpath, truffleClassloader);
            appManager = new ApplicationManagerInvoker(classLoader);
            appManager.startApplication(parameters);
            // Set up file watching
            var watchService = FileSystems.getDefault().newWatchService();
            for (var watchedDirectory : WATCHED_DIRECTORIES) {
                var dir = sourceDirectory.resolve(watchedDirectory);
                if (Files.isDirectory(dir)) {
                    registerAll(dir, watchService);
                    System.out.println("Watching for changes in " + dir);
                }
            }

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
                        long sd = System.nanoTime();
                        // TODO: ideally we should keep the existing app alive until
                        // we know that all files are compiled properly. This is difficult
                        // to implement because the generated classes contain an absolute
                        // path to where the files are generated, which makes it impossible
                        // to use a different directory for compilation and runtime.
                        appManager.stopApplication();
                        System.out.println("Changes detected, recompiling and restarting...");
                        if (compile(truffleClassloader) == SUCCESS) {
                            appManager.startApplication(parameters);
                            long ed = System.nanoTime();
                            var dur = Duration.ofNanos(ed - sd).toMillis();
                            System.out.println("Restart done in " + dur + "ms");
                        } else {
                            System.err.println("Compilation failed, application not restarted");
                        }
                    }
                    starting.set(false);
                }

                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            if (appManager != null) {
                appManager.stopApplication();
            }
        }
    }

    private URLClassLoader createTruffleClassLoader(PythonMavenRepository repo) {
        var urls = repo.visitRepo(PyronautFileWatcher::isTruffleJar)
            .stream()
            .map(f -> {
                try {
                    return f.toURI().toURL();
                } catch (MalformedURLException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList()
            .toArray(new URL[0]);
        var parent = this.getClass().getClassLoader().getParent();
        return new URLClassLoader(urls, parent);
    }

    /**
     * Determines if a Maven artifact is supposed to belong to the
     * Truffle/GraalVM engine, in which case it needs to be put in
     * a parent classloader.
     *
     * @param name the name of a file
     * @return true if it belongs to the Truffle runtime
     */
    private static boolean isTruffleJar(MavenArtifact name) {
        if (name.groupId().startsWith("org.graalvm")) {
            return true;
        }
        if (name.groupId().equals("org.bouncycastle")) {
            return true;
        }
        return false;
    }

    private void registerAll(Path start, WatchService watchService) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
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

    private int compile(ClassLoader truffleClassloader) {
        var compiler = new PyronautCliCompiler();
        compiler.classLoader = truffleClassloader;
        compiler.sourceDirectory = sourceDirectory.toFile();
        compiler.outputDirectory = classesDirectory().toFile();
        compiler.annotationProcessorPath = annotationProcessorRepo.asClasspath();
        compiler.classpath = compileClassPathRepo.asClasspath();
        System.out.println("Compiling...");
        try {
            recurseDelete(classesDirectory());
            createDirectories(classesDirectory());
            return compiler.call();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            return ERROR;
        }
    }

    private Path classesDirectory() {
        return outputDirectory.resolve(FileUtils.CLASSES_DIR);
    }

    public void stop() {
        running.set(false);
    }

    private URL[] buildUrls(Path... paths) throws MalformedURLException {
        var compileClassPath = compileClassPathRepo.visitRepo(a -> !isTruffleJar(a));
        var result = new ArrayList<URL>(1 + paths.length + compileClassPath.size());
        for (var path : paths) {
            result.add(path.toUri().toURL());
        }
        for (var file : compileClassPath) {
            var url = file.toURI().toURL();
            result.add(url);
        }
        // This is a hack, so that the launcher is on classpath of the user app
        // and it won't work in a native image
        result.add(PyronautFileWatcher.class.getProtectionDomain().getCodeSource().getLocation());
        return result.toArray(new URL[0]);
    }

    /**
     * This application manager is used to invoke an application "reflectively"
     * using method handles. This is done because we have to isolate the classloader
     * of the compiler from the application classloader. Therefore, we cannot use
     * types from Micronaut Context (e.g ApplicationContext) directly, because they
     * would be loaded from different classloaders.
     */
    private static class ApplicationManagerInvoker implements ApplicationManager {
        private final MethodHandle constructor;
        private final MethodHandle startMethod;
        private final MethodHandle stoptMethod;
        private final Object applicationManager;

        private ApplicationManagerInvoker(ClassLoader classLoader) {
            try {
                var clazz =
                    classLoader.loadClass("io.micronaut.python.cli.DefaultApplicationManager");
                var lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
                var voidType = MethodType.methodType(void.class);
                constructor = lookup.findConstructor(clazz, voidType);
                startMethod = lookup.findVirtual(clazz, "startApplication",
                    MethodType.methodType(void.class, String[].class));
                stoptMethod = lookup.findVirtual(clazz, "stopApplication", voidType);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            applicationManager = newManager();
        }

        private Object newManager() {
            try {
                return constructor.invoke();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void startApplication(String[] args) {
            try {
                startMethod.invoke(applicationManager, args);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void stopApplication() {
            try {
                stoptMethod.invoke(applicationManager);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
