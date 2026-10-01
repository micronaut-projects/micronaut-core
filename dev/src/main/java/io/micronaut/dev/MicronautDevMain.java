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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevMode;
import io.micronaut.dev.manifest.ResourceRoot;
import io.micronaut.dev.test.TestRunSummary;
import org.jspecify.annotations.NullMarked;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The entry point of development mode: {@code java -cp @runtime.argfile io.micronaut.dev.MicronautDevMain --manifest build/micronaut-dev/dev.properties}.
 * Reads the manifest, builds the reloadable class loader over the project's outputs, starts the
 * {@link DevRuntime} and runs the application's own main through it, or in test mode its tests.
 *
 * <p>Open for subclassing: a launcher for another language, such as Pyronaut, overrides the template
 * methods to add compilers, choose the loader's roots, or run something other than a Java main.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public class MicronautDevMain {

    /**
     * The argument naming the manifest.
     */
    public static final String MANIFEST_OPTION = "--manifest";

    /**
     * What {@link #runForStatus(String[])} returns while the runtime keeps running in the background.
     */
    public static final int RUNNING = -1;

    /**
     * Runs the launcher.
     *
     * @param args {@code --manifest <file>}, or the {@code micronaut.dev.manifest} system property, followed by the application's arguments
     * @throws Exception if the launch fails
     */
    public static void main(String[] args) throws Exception {
        int status = new MicronautDevMain().runForStatus(args);
        if (status != RUNNING) {
            System.exit(status);
        }
    }

    /**
     * Runs the launcher with the given arguments and keeps the JVM alive while the application runs.
     *
     * @param args The arguments
     * @throws Exception if the launch fails
     */
    public void run(String[] args) throws Exception {
        runForStatus(args);
    }

    /**
     * Runs the launcher with the given arguments and keeps the JVM alive while the application, or in test mode the
     * watching, runs. In test mode the call returns once the runtime is closed, with the status the process exits with:
     * 0 when the last run passed, 1 otherwise; with {@code micronaut.dev.test.once} the tests run once first. On a terminal, test mode also reads single keys: space
     * runs the last tests again, {@code a} every test, {@code f} the failures, {@code w} turns watching on or off, and
     * {@code q} exits with the last run's status.
     *
     * @param args The arguments
     * @return The status to exit with, or {@link #RUNNING} while the runtime runs in the background
     * @throws Exception if the launch fails
     */
    public int runForStatus(String[] args) throws Exception {
        List<String> remaining = new ArrayList<>();
        String manifestPath = System.getProperty(DevManifest.MANIFEST_PROPERTY);
        for (int i = 0; i < args.length; i++) {
            if (MANIFEST_OPTION.equals(args[i]) && i + 1 < args.length) {
                manifestPath = args[++i];
            } else if (args[i].startsWith(MANIFEST_OPTION + "=")) {
                manifestPath = args[i].substring(MANIFEST_OPTION.length() + 1);
            } else {
                remaining.add(args[i]);
            }
        }
        if (manifestPath == null) {
            throw new IllegalArgumentException("No manifest: pass " + MANIFEST_OPTION + " <file> or set -D" + DevManifest.MANIFEST_PROPERTY);
        }
        DevManifest manifest = DevManifest.load(Path.of(manifestPath));
        DevRuntime runtime = launch(manifest, remaining.toArray(new String[0]));
        if (manifest.mode() == DevMode.TEST && manifest.testSettings().once()) {
            TestRunSummary summary = runtime.lastTestRun().orElse(null);
            runtime.close();
            return summary != null && summary.isSuccess() ? 0 : 1;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "micronaut-dev-shutdown"));
        if (manifest.mode() == DevMode.TEST) {
            // no application thread keeps the JVM alive in test mode: the launcher waits until the runtime is closed
            TestConsole.startIfInteractive(runtime);
            runtime.awaitClose();
            TestRunSummary summary = runtime.lastTestRun().orElse(null);
            return summary != null && summary.isSuccess() ? 0 : 1;
        }
        return RUNNING;
    }

    /**
     * Starts the runtime for a manifest: the loader, the watcher and the application, returning once
     * the first generation started. The application runs on its own thread.
     *
     * @param manifest The manifest
     * @param args The application's arguments
     * @return The runtime, to close when done
     */
    public DevRuntime launch(DevManifest manifest, String[] args) {
        Map<SourceKind, SourceCompiler> compilers = createCompilers(manifest);
        // a clean checkout has no class output yet: compile before the loader snapshots the roots
        DevRuntime.compileMissingOutputs(manifest, compilers);
        if (manifest.mode() == DevMode.TEST) {
            DevRuntime.compileMissingOutputs(manifest.testView(), compilers);
        }
        DevClassLoader classLoader = createClassLoader(manifest);
        DevRuntime runtime = new DevRuntime(manifest, classLoader, this::launchApplication, compilers);
        if (manifest.mode() == DevMode.TEST) {
            runtime.startTests();
        } else {
            runtime.start(args);
        }
        return runtime;
    }

    /**
     * Creates the reloadable loader: parent-first over the launcher's own loader, which holds the
     * runtime jars, with the manifest's resource roots and then its reloadable roots as the child tier.
     *
     * @param manifest The manifest
     * @return The loader
     */
    protected DevClassLoader createClassLoader(DevManifest manifest) {
        ClassLoader parent = parentClassLoader();
        Path generations = manifest.generations();
        // the resource roots are read live, ahead of the snapshotted build output, so an edited configuration
        // file or template is what a generation serves, without a copy step by the build
        List<Path> live = new ArrayList<>();
        for (ResourceRoot resourceRoot : manifest.resourceRoots()) {
            if (Files.isDirectory(resourceRoot.path())) {
                live.add(resourceRoot.path());
            }
        }
        return new DevClassLoader(parent, live, manifest.reloadableRoots(), generations);
    }

    /**
     * @return The loader the runtime jars are loaded by, the parent of every generation
     */
    protected ClassLoader parentClassLoader() {
        ClassLoader parent = MicronautDevMain.class.getClassLoader();
        return parent != null ? parent : ClassLoader.getSystemClassLoader();
    }

    /**
     * The compilers to use, by language: the ones registered as services and available in this JVM.
     *
     * @param manifest The manifest
     * @return The compilers
     */
    protected Map<SourceKind, SourceCompiler> createCompilers(DevManifest manifest) {
        return DevRuntime.availableCompilers();
    }

    /**
     * Runs the application: loads the main class through the current generation's loader and invokes
     * its {@code main}. Returns when main returns.
     *
     * @param classLoader The current generation's loader
     * @param mainClass The main class name
     * @param args The arguments
     * @throws Exception if main throws
     */
    protected void launchApplication(ClassLoader classLoader, String mainClass, String[] args) throws Exception {
        Class<?> type = Class.forName(mainClass, true, classLoader);
        Method main = type.getMethod("main", String[].class);
        if (!Modifier.isPublic(type.getModifiers())) {
            // the java launcher runs the public main of a class that is not public, as the Pyronaut compiler generates
            main.setAccessible(true);
        }
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

}
