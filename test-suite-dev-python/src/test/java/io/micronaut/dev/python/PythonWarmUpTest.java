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
package io.micronaut.dev.python;

import io.micronaut.dev.PythonWarmUp;
import io.micronaut.dev.manifest.DevManifest;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launch of a Python application initializes a GraalPy context on a thread of its own, and no other launch does.
 */
class PythonWarmUpTest {

    private static final String WARM_UP = "micronaut.dev.python.warm-up";
    private static final String PYTHON_ENABLED = "micronaut.python.enabled";
    private static final String PYTHON_SOURCES = "micronaut.dev.sources.python=src/main/python\n";

    @TempDir
    Path project;

    @AfterEach
    void clearProperties() {
        System.clearProperty(WARM_UP);
        System.clearProperty(PYTHON_ENABLED);
    }

    @Test
    void aPythonApplicationWarmsUpOnADaemonThreadOfTheLauncherLoaderWithoutWritingAnything() throws Exception {
        ClassLoader parent = PythonWarmUpTest.class.getClassLoader();
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut(new PrintStream(written));
        System.setErr(new PrintStream(written));
        try {
            Thread thread = PythonWarmUp.start(manifest(PYTHON_SOURCES), parent);
            assertNotNull(thread);
            assertTrue(thread.isDaemon());
            assertSame(parent, thread.getContextClassLoader());
            thread.join(TimeUnit.MINUTES.toMillis(2));
            assertFalse(thread.isAlive(), "the warm-up did not end");
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        assertEquals("", written.toString(), "the warm-up wrote to the application's output");
        // the application's context comes after it, in the same JVM
        try (Context context = Context.newBuilder("python").allowExperimentalOptions(true)
            .option("engine.WarnInterpreterOnly", "false").build()) {
            assertEquals(2, context.eval("python", "1 + 1").asInt());
        }
    }

    @Test
    void anApplicationWithoutPythonSourcesStartsNothing() throws Exception {
        assertNull(PythonWarmUp.start(manifest("micronaut.dev.sources.java=src/main/java\n"), PythonWarmUpTest.class.getClassLoader()));
    }

    @Test
    void pythonTestsWarmUpInTestModeOnly() throws Exception {
        ClassLoader parent = PythonWarmUpTest.class.getClassLoader();
        String tests = "micronaut.dev.sources.java=src/main/java\nmicronaut.dev.test.sources.python=src/test/python\n";
        assertNull(PythonWarmUp.start(manifest(tests), parent));
        Thread thread = PythonWarmUp.start(manifest(tests + "micronaut.dev.mode=test\n"), parent);
        assertNotNull(thread);
        thread.join(TimeUnit.MINUTES.toMillis(2));
        assertFalse(thread.isAlive(), "the warm-up did not end");
    }

    @Test
    void aLauncherWithoutThePythonRuntimeStartsNothing() throws Exception {
        try (URLClassLoader noRuntime = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES), noRuntime));
        }
    }

    @Test
    void anApplicationThatSwitchedPythonOffStartsNothing() throws Exception {
        System.setProperty(PYTHON_ENABLED, "false");
        assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES), PythonWarmUpTest.class.getClassLoader()));
    }

    @Test
    void itCanBeSwitchedOff() throws Exception {
        System.setProperty(WARM_UP, "false");
        assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES), PythonWarmUpTest.class.getClassLoader()));
    }

    private DevManifest manifest(String sources) throws IOException {
        Files.createDirectories(project.resolve("build/classes"));
        Path file = project.resolve("dev.properties");
        Files.writeString(file, """
            micronaut.dev.main-class=app.Main
            micronaut.dev.project-dir=.
            micronaut.dev.reloadable=build/classes
            """ + sources);
        return DevManifest.load(file);
    }
}
