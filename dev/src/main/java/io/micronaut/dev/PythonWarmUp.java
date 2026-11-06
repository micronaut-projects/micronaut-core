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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevMode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;

/**
 * Initializes a GraalPy context on a thread of its own as a Python application launches, and closes it. Most of what
 * the first context of a JVM costs is loading and initializing GraalPy's classes, once for the JVM, on the thread that
 * starts the application. The launch spends about a second on other work before it gets there, and this thread uses
 * that second: the context of the application then finds the work done. Started any later, it gains nothing, as the
 * two threads then wait for each other on the same classes.
 *
 * <p>Only a launch with Python sources, or in test mode Python tests, starts it, and only on a JVM: a native image has no classes to load.
 * {@code micronaut.dev.python.warm-up=false} turns it off.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class PythonWarmUp {

    private static final Logger LOG = LoggerFactory.getLogger(PythonWarmUp.class);
    private static final String PYTHON = "python";
    private static final String PYTHON_RUNTIME = "io.micronaut.context.python.GraalPyContextFactory";
    private static final String PYTHON_ENABLED = "micronaut.python.enabled";
    private static final String PYTHON_ENABLED_VARIABLE = "MICRONAUT_PYTHON_ENABLED";
    private static final String WARN_INTERPRETER_ONLY = "engine.WarnInterpreterOnly";
    private static final String COMPILATION = "engine.Compilation";
    private static final String COMPILER_THREADS = "engine.CompilerThreads";
    private static final String FALSE = Boolean.FALSE.toString();

    private PythonWarmUp() {
    }

    /**
     * Starts the warm-up, if the launch is one of a Python application.
     *
     * @param manifest The manifest of the launch
     * @param parent The loader of the launcher, which outlives the generations
     * @return The thread, or null when there is nothing to warm up
     */
    public static @Nullable Thread start(DevManifest manifest, ClassLoader parent) {
        if (!manifest.pythonWarmUp()
            || !hasPythonSources(manifest)
            || System.getProperty("org.graalvm.nativeimage.imagecode") != null
            || FALSE.equalsIgnoreCase(System.getProperty(PYTHON_ENABLED))
            || FALSE.equalsIgnoreCase(System.getenv(PYTHON_ENABLED_VARIABLE))
            || !ClassUtils.isPresent(PYTHON_RUNTIME, parent)) {
            return null;
        }
        Thread thread = new Thread(PythonWarmUp::warmUp, "micronaut-dev-python-warm-up");
        thread.setDaemon(true);
        // the first engine of the JVM registers JVM-wide shutdown hooks on its thread, which keep this loader
        thread.setContextClassLoader(parent);
        thread.start();
        return thread;
    }

    private static boolean hasPythonSources(DevManifest manifest) {
        return !manifest.sourceRoots(SourceKind.PYTHON).isEmpty()
            || manifest.mode() == DevMode.TEST && manifest.testSourceRoots().stream().anyMatch(root -> root.kind() == SourceKind.PYTHON);
    }

    private static void warmUp() {
        try {
            Context.Builder builder = Context.newBuilder(PYTHON)
                .allowExperimentalOptions(true)
                .option(WARN_INTERPRETER_ONLY, FALSE)
                // what the application's context uses is its own to load
                .option("python.PosixModuleBackend", "java")
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream());
            if (isOptimizingRuntime()) {
                // the first engine of the JVM with a compiler thread, which one with compilation off still gets, sizes
                // the compiler queue of all: this is the one thread the application's engine asks for by default
                builder.option(COMPILATION, FALSE).option(COMPILER_THREADS, "1");
            }
            try (Context context = builder.build()) {
                context.initialize(PYTHON);
            }
        } catch (RuntimeException | LinkageError e) {
            // only slower: the application's context does this work itself, and reports what is wrong with it
            LOG.debug("Could not warm up GraalPy", e);
        }
    }

    /**
     * @return Whether the active Truffle runtime knows the compiler options, which only the optimizing runtime does
     */
    private static boolean isOptimizingRuntime() {
        try (Engine engine = Engine.newBuilder().option(WARN_INTERPRETER_ONLY, FALSE).build()) {
            return engine.getOptions().get(COMPILATION) != null;
        }
    }
}
