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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Which launches start the Python warm-up, and what the warm-up does where the Python runtime is on the classpath but
 * GraalPy's language is not: the polyglot API is on this module's test classpath, GraalPy is not, so these run in the
 * Java build. {@code test-suite-dev-python} warms up a real GraalPy.
 */
class PythonWarmUpStartTest {

    private static final String PYTHON_RUNTIME = "io.micronaut.context.python.GraalPyContextFactory";
    private static final String PYTHON_ENABLED = "micronaut.python.enabled";
    private static final String IMAGE_CODE = "org.graalvm.nativeimage.imagecode";
    private static final String PYTHON_SOURCES = "micronaut.dev.sources.python";
    private static final String PYTHON_TESTS = "micronaut.dev.test.sources.python";
    private static final String JAVA_SOURCES = "micronaut.dev.sources.java";
    private static final String JAVA_TESTS = "micronaut.dev.test.sources.java";
    private static final String MODE = "micronaut.dev.mode";

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger warmUpLogger = (Logger) LoggerFactory.getLogger(PythonWarmUp.class);
    private Level previousLevel;
    private boolean previousAdditive;

    @TempDir
    Path project;

    @BeforeEach
    void captureTheWarmUpLog() {
        previousLevel = warmUpLogger.getLevel();
        previousAdditive = warmUpLogger.isAdditive();
        logged.start();
        warmUpLogger.addAppender(logged);
        warmUpLogger.setLevel(Level.DEBUG);
        warmUpLogger.setAdditive(false);
    }

    @AfterEach
    void restore() {
        warmUpLogger.detachAppender(logged);
        warmUpLogger.setLevel(previousLevel);
        warmUpLogger.setAdditive(previousAdditive);
        logged.stop();
        System.clearProperty(PYTHON_ENABLED);
    }

    @Test
    void aPythonApplicationWarmsUpOnADaemonThreadOfTheLauncherLoaderAndGivesUpQuietlyWithoutGraalPy() throws Exception {
        assumeFalse("false".equalsIgnoreCase(System.getenv("MICRONAUT_PYTHON_ENABLED")), "Python is switched off in the environment");
        ClassLoader parent = new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader());
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        PrintStream out = System.out;
        PrintStream err = System.err;
        System.setOut(new PrintStream(written, true));
        System.setErr(new PrintStream(written, true));
        try {
            Thread thread = PythonWarmUp.start(manifest(PYTHON_SOURCES, "src/main/python"), parent);
            assertNotNull(thread);
            assertEquals("micronaut-dev-python-warm-up", thread.getName());
            assertTrue(thread.isDaemon());
            assertSame(parent, thread.getContextClassLoader());
            thread.join(TimeUnit.MINUTES.toMillis(1));
            assertFalse(thread.isAlive(), "the warm-up did not end");
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        assertEquals("", written.toString(), "the warm-up wrote to the application's output");
        // the application's context reports what is wrong with GraalPy, the warm-up only at debug level
        List<ILoggingEvent> events = logged.list;
        assertEquals(1, events.size(), () -> "logged " + events);
        ILoggingEvent event = events.get(0);
        assertEquals(Level.DEBUG, event.getLevel());
        assertEquals("Could not warm up GraalPy", event.getFormattedMessage());
        assertNotNull(event.getThrowableProxy());
        assertEquals(IllegalArgumentException.class.getName(), event.getThrowableProxy().getClassName());
    }

    @Test
    void pythonTestsWarmUpInTestModeOnly() throws Exception {
        ClassLoader parent = new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader());
        // a Python test root among others, in test mode
        Thread thread = PythonWarmUp.start(manifest(
            JAVA_SOURCES, "src/main/java",
            JAVA_TESTS, "src/test/java",
            PYTHON_TESTS, "src/test/python",
            MODE, "test"), parent);
        assertNotNull(thread);
        thread.join(TimeUnit.MINUTES.toMillis(1));
        assertFalse(thread.isAlive(), "the warm-up did not end");
        // the application runs, its Python tests do not
        assertNull(PythonWarmUp.start(manifest(JAVA_SOURCES, "src/main/java", PYTHON_TESTS, "src/test/python"), parent));
        // test mode without Python tests
        assertNull(PythonWarmUp.start(manifest(JAVA_SOURCES, "src/main/java", JAVA_TESTS, "src/test/java", MODE, "test"), parent));
    }

    @Test
    void anApplicationWithoutPythonSourcesStartsNothing() {
        ClassLoader parent = new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader());
        assertNull(PythonWarmUp.start(manifest(JAVA_SOURCES, "src/main/java"), parent));
    }

    @Test
    void itCanBeSwitchedOffInTheManifest() {
        ClassLoader parent = new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader());
        assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES, "src/main/python", "micronaut.dev.python.warm-up", "false"), parent));
    }

    @Test
    void anApplicationThatSwitchedPythonOffStartsNothing() {
        ClassLoader parent = new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader());
        System.setProperty(PYTHON_ENABLED, "FALSE");
        assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES, "src/main/python"), parent));
    }

    @Test
    void aNativeImageStartsNothing() {
        ClassLoader parent = new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader());
        String previous = System.setProperty(IMAGE_CODE, "runtime");
        try {
            assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES, "src/main/python"), parent));
        } finally {
            if (previous == null) {
                System.clearProperty(IMAGE_CODE);
            } else {
                System.setProperty(IMAGE_CODE, previous);
            }
        }
    }

    @Test
    void aLauncherWithoutThePythonRuntimeStartsNothing() throws Exception {
        try (URLClassLoader noRuntime = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES, "src/main/python"), noRuntime));
        }
        assertTrue(logged.list.isEmpty());
    }

    @Test
    void theStubRuntimeIsTheOnlyOneOnTheTestClasspath() throws Exception {
        // the launches above see the Python runtime only through the stub loader
        assertNull(PythonWarmUp.start(manifest(PYTHON_SOURCES, "src/main/python"), PythonWarmUpStartTest.class.getClassLoader()));
        Class<?> stub = Class.forName(PYTHON_RUNTIME, false, new PythonRuntimeLoader(PythonWarmUpStartTest.class.getClassLoader()));
        assertInstanceOf(PythonRuntimeLoader.class, stub.getClassLoader());
    }

    private DevManifest manifest(String... entries) {
        Properties properties = new Properties();
        properties.setProperty("micronaut.dev.main-class", "app.Main");
        properties.setProperty("micronaut.dev.reloadable", "build/classes");
        for (int i = 0; i < entries.length; i += 2) {
            properties.setProperty(entries[i], entries[i + 1]);
        }
        return DevManifest.of(project, properties);
    }

    /**
     * Defines an empty class by the name of the Python runtime's context factory: what the warm-up checks for.
     */
    private static final class PythonRuntimeLoader extends ClassLoader {

        PythonRuntimeLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!PYTHON_RUNTIME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = ClassFile.of().build(ClassDesc.of(name), builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL)
                .withSuperclass(ConstantDescs.CD_Object));
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
