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
package io.micronaut.python.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.micronaut.python.processing.PythonProcessingSession;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PyronautCompilerIncrementalTest {
    private static final FileTime UNCHANGED_MARKER = FileTime.fromMillis(1_000);

    @Test
    void incrementalCompilationIsDisabledByDefault(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path cache = directory.resolve("incremental");
        Files.writeString(sources.resolve("Greeting.java"), "public class Greeting {}\n");

        PyronautCompiler.builder()
            .javaSrc(sources.toString())
            .targetDir(output.toFile())
            .incrementalCacheDirectory(cache.toFile())
            .build()
            .compile();

        assertFalse(Files.exists(cache));
    }

    @Test
    void restoresSystemPropertiesChangedByAnnotationProcessors(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = Files.createDirectories(directory.resolve("classes"));
        Files.writeString(sources.resolve("Greeting.java"), "public class Greeting {}\n");
        String property = "micronaut.test.compiler.processor.option";
        String previous = System.getProperty(property);
        Properties systemProperties = System.getProperties();
        System.setProperty(property, "before");
        try {
            PyronautCompiler.builder()
                .javaSrc(sources.toString())
                .targetDir(output.toFile())
                .annotationProcessors(List.of(new SystemPropertyMutatingProcessor(property)))
                .build()
                .compile();

            assertEquals("before", System.getProperty(property));
            assertSame(systemProperties, System.getProperties());
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void packageWildcardOnlySelectsSourcesImportingThatAnnotationPackage(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path pythonController = Files.writeString(python.resolve("controller.py"), """
            from micronaut.http.annotation import Get
            @Get("/")
            def index():
                return "ok"
            """);
        Path unrelatedPython = Files.writeString(python.resolve("seed.py"), "value = 1\n");
        Path javaController = Files.writeString(java.resolve("Controller.java"), """
            import io.micronaut.http.annotation.Get;
            class Controller {
                @Get("/") String index() { return "ok"; }
            }
            """);
        Path unrelatedJava = Files.writeString(java.resolve("Seed.java"), "class Seed {}\n");

        Set<String> matches = PyronautJavaCompiler.sourcesUsingAnnotations(
            Set.of("io.micronaut.http.annotation.*"),
            java.toString(),
            python.toString()
        );

        assertEquals(Set.of(
            pythonController.toRealPath().toString(),
            javaController.toRealPath().toString()
        ), matches);
        assertFalse(matches.contains(unrelatedPython.toRealPath().toString()));
        assertFalse(matches.contains(unrelatedJava.toRealPath().toString()));
    }

    @Test
    void reusesPythonProcessingSessionAcrossIncrementalCompilations(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path cache = directory.resolve("incremental");
        Path source = python.resolve("example.py");
        Files.writeString(source, "class Example:\n    value: int = 1\n");

        try (PythonProcessingSession session = new PythonProcessingSession()) {
            compilePython(python, java, output, cache, session);
            assertTrue(session.initialized());

            Files.writeString(source, "class Example:\n    value: int = 2\n");
            compilePython(python, java, output, cache, session);
            assertTrue(Files.readString(output.resolve(
                "META-INF/GRAALPY-VFS/micronaut-application/src/example.py"
            )).contains("value: int = 2"));
        }
    }

    @Test
    void reusablePythonSessionPreservesAnnotationMetadata(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = Files.createDirectories(directory.resolve("classes"));
        Path httpClasspath = Path.of(io.micronaut.http.annotation.Controller.class
            .getProtectionDomain().getCodeSource().getLocation().toURI());
        Path source = python.resolve("controller.py");
        Files.writeString(source, """
            from micronaut.http.annotation import Controller, Get
            @Controller("/")
            class Example:
                @Get("/")
                def index(self) -> str:
                    return "first"
            """);

        try (PythonProcessingSession session = new PythonProcessingSession()) {
            assertPythonControllerAnnotations(python, java, output, httpClasspath, session);
            Files.writeString(source, Files.readString(source).replace("first", "second"));
            assertPythonControllerAnnotations(python, java, output, httpClasspath, session);
        }
    }

    @Test
    void reusablePythonSessionRetainsTheProcessorClassLoader(@TempDir Path directory) throws Exception {
        Path processorPath = Files.writeString(directory.resolve("processors.jar"), "test");
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            ClassLoader first = session.classLoader(
                List.of(processorPath.toFile()),
                () -> new java.net.URLClassLoader(new java.net.URL[0], getClass().getClassLoader())
            );
            ClassLoader second = session.classLoader(
                List.of(processorPath.toFile()),
                () -> new java.net.URLClassLoader(new java.net.URL[0], getClass().getClassLoader())
            );

            assertSame(first, second);

            Files.writeString(processorPath, "changed");
            ClassLoader changed = session.classLoader(
                List.of(processorPath.toFile()),
                () -> new java.net.URLClassLoader(new java.net.URL[0], getClass().getClassLoader())
            );
            assertNotSame(first, changed);
        }
    }

    @Test
    void reusablePythonSessionInvalidatesTheProcessorClassLoaderForExplodedClasses(
        @TempDir Path directory) throws Exception {
        Path processorPath = Files.createDirectories(directory.resolve("processor-classes"));
        Path processorClass = Files.writeString(processorPath.resolve("Example.class"), "first");
        FileTime directoryTime = Files.getLastModifiedTime(processorPath);
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            ClassLoader first = session.classLoader(
                List.of(processorPath.toFile()),
                () -> new java.net.URLClassLoader(new java.net.URL[0], getClass().getClassLoader())
            );

            Files.writeString(processorClass, "changed");
            Files.setLastModifiedTime(processorPath, directoryTime);
            ClassLoader changed = session.classLoader(
                List.of(processorPath.toFile()),
                () -> new java.net.URLClassLoader(new java.net.URL[0], getClass().getClassLoader())
            );

            assertNotSame(first, changed);
        }
    }

    @Test
    void reportsSourcesSelectedForIncrementalCompilation(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = Files.writeString(
            sources.resolve("Alpha.java"),
            "public class Alpha { int value() { return 1; } }\n"
        );
        Path beta = Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");
        List<PyronautCompiler.IncrementalCompilationPlan> plans = new ArrayList<>();

        compileJavaWithPlanCallback(sources, output, cache, plans);
        assertEquals(1, plans.size());
        assertTrue(plans.getFirst().fullRebuild());
        assertEquals(Set.of(alpha.toRealPath(), beta.toRealPath()), Set.copyOf(plans.getFirst().sources()));

        plans.clear();
        Files.writeString(alpha, "public class Alpha { int value() { return 2; } }\n");
        compileJavaWithPlanCallback(sources, output, cache, plans);
        assertEquals(1, plans.size());
        assertFalse(plans.getFirst().fullRebuild());
        assertFalse(plans.getFirst().upToDate());
        assertEquals(List.of(alpha.toRealPath()), plans.getFirst().sources());

        plans.clear();
        compileJavaWithPlanCallback(sources, output, cache, plans);
        assertEquals(1, plans.size());
        assertTrue(plans.getFirst().upToDate());
        assertTrue(plans.getFirst().sources().isEmpty());
    }

    @Test
    void recompilesOnlyChangedIndependentJavaSources(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha { int value() { return 1; } }\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileJava(sources, output, cache);
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);

        Files.writeString(alpha, "public class Alpha { int value() { return 2; } }\n");
        compileJava(sources, output, cache);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
        assertTrue(Files.isRegularFile(cache.resolve("state.properties")));
    }

    @Test
    void noChangeCompilationLeavesOutputsUntouched(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Files.writeString(sources.resolve("Alpha.java"), "public class Alpha {}\n");

        compileJava(sources, output, cache);
        Path alphaClass = output.resolve("Alpha.class");
        Files.setLastModifiedTime(alphaClass, UNCHANGED_MARKER);

        compileJava(sources, output, cache);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(alphaClass));
    }

    @Test
    void tracksJavaIsolatingVisitorOutputsAcrossAddDeleteAndRename(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path one = sources.resolve("One.java");
        Path two = sources.resolve("Two.java");
        Files.writeString(one, """
            import io.micronaut.python.compiler.TestIsolate;
            @TestIsolate
            public class One {}
            """);
        Files.writeString(two, """
            import io.micronaut.python.compiler.TestIsolate;
            @TestIsolate
            public class Two {}
            """);

        compileJava(sources, output, cache);
        Path oneOutput = output.resolve("META-INF/pyronaut/isolating-One.txt");
        Path twoOutput = output.resolve("META-INF/pyronaut/isolating-Two.txt");
        Files.setLastModifiedTime(twoOutput, UNCHANGED_MARKER);

        Files.writeString(one, """
            import io.micronaut.python.compiler.TestIsolate;
            @TestIsolate
            public class One { int changed; }
            """);
        compileJava(sources, output, cache);
        assertTrue(Files.isRegularFile(oneOutput));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(twoOutput));

        Files.delete(one);
        Path renamed = sources.resolve("Renamed.java");
        Files.writeString(renamed, """
            import io.micronaut.python.compiler.TestIsolate;
            @TestIsolate
            public class Renamed {}
            """);
        compileJava(sources, output, cache);

        assertFalse(Files.exists(oneOutput));
        assertFalse(Files.exists(output.resolve("One.class")));
        assertTrue(Files.isRegularFile(output.resolve("META-INF/pyronaut/isolating-Renamed.txt")));
        assertTrue(Files.isRegularFile(output.resolve("Renamed.class")));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(twoOutput));
    }

    @Test
    void tracksClassesGeneratedFromIsolatingProcessorSources(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, """
            import io.micronaut.python.compiler.TestIsolate;
            @TestIsolate
            public class Alpha {}
            """);
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileWithProcessor(
            sources,
            output,
            cache,
            new TestIsolatingSourceGeneratingProcessor()
        );
        Path generatedClass = output.resolve("generated/UnrelatedGenerated.class");
        Path betaClass = output.resolve("Beta.class");
        assertTrue(Files.isRegularFile(generatedClass));
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);

        Files.writeString(alpha, """
            import io.micronaut.python.compiler.TestIsolate;
            @TestIsolate
            public class Alpha { int changed; }
            """);
        compileWithProcessor(
            sources,
            output,
            cache,
            new TestIsolatingSourceGeneratingProcessor()
        );

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
        assertTrue(Files.isRegularFile(generatedClass));
        assertEquals(
            "true",
            state(cache).getProperty("processor.compatible"),
            Files.readString(cache.resolve("state.properties"))
        );

        Files.delete(alpha);
        compileWithProcessor(
            sources,
            output,
            cache,
            new TestIsolatingSourceGeneratingProcessor()
        );
        assertFalse(Files.exists(generatedClass));
    }

    @Test
    void recompilesTransitiveJavaDependents(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha { int value() { return 1; } }\n");
        Files.writeString(
            sources.resolve("Beta.java"),
            "public class Beta { Alpha alpha = new Alpha(); }\n"
        );

        compileJava(sources, output, cache);
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);

        Files.writeString(alpha, "public class Alpha { int value() { return 2; } }\n");
        compileJava(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
    }

    @Test
    void removesOutputsForDeletedJavaSources(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha {}\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileJava(sources, output, cache);
        assertTrue(Files.deleteIfExists(alpha));
        compileJava(sources, output, cache);

        assertFalse(Files.exists(output.resolve("Alpha.class")));
        assertTrue(Files.isRegularFile(output.resolve("Beta.class")));
    }

    @Test
    void preservesUnchangedPythonSourceOutputs(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, """
            from jakarta.inject import Singleton
            @Singleton
            class Alpha:
                value: int = 1
            """);
        Files.writeString(python.resolve("beta.py"), """
            from jakarta.inject import Singleton
            @Singleton
            class Beta:
                value: int = 2
            """);

        compilePython(python, java, output, cache);
        Path betaVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/beta.py"
        );
        Path alphaBean = findOutput(output, "Alpha$Definition.class");
        Path betaBean = findOutput(output, "Beta$Definition.class");
        Files.setLastModifiedTime(betaVfs, UNCHANGED_MARKER);
        Files.setLastModifiedTime(betaBean, UNCHANGED_MARKER);

        Files.writeString(alpha, """
            from jakarta.inject import Singleton
            @Singleton
            class Alpha:
                value: int = 3
            """);
        compilePython(python, java, output, cache);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaVfs));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaBean));
        assertTrue(Files.readString(output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/alpha.py"
        )).contains("3"));

        Files.delete(alpha);
        compilePython(python, java, output, cache);
        assertFalse(Files.exists(alphaBean));
        assertTrue(Files.isRegularFile(betaBean));
    }

    @Test
    void preservesUnchangedPythonIsolatingVisitorAndBytecodeOutputs(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, """
            from io.micronaut.python.compiler import TestIsolate
            @TestIsolate
            class Alpha:
                value: int = 1
            """);
        Files.writeString(python.resolve("beta.py"), """
            from io.micronaut.python.compiler import TestIsolate
            @TestIsolate
            class Beta:
                value: int = 2
            """);

        compilePython(python, java, output, cache, true);
        Path betaVisitorOutput = output.resolve("META-INF/pyronaut/isolating-Beta.txt");
        Path alphaBytecode = findOutput(output, "alpha.", ".pyc");
        Path betaBytecode = findOutput(output, "beta.", ".pyc");
        Files.setLastModifiedTime(betaVisitorOutput, UNCHANGED_MARKER);
        Files.setLastModifiedTime(betaBytecode, UNCHANGED_MARKER);

        Files.writeString(alpha, """
            from io.micronaut.python.compiler import TestIsolate
            @TestIsolate
            class Alpha:
                value: int = 3
            """);
        compilePython(python, java, output, cache, true);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaVisitorOutput));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaBytecode));

        Files.delete(alpha);
        compilePython(python, java, output, cache, true);
        assertFalse(Files.exists(alphaBytecode));
        assertTrue(Files.isRegularFile(betaBytecode));
    }

    @Test
    void tracksMandatoryRuntimeBytecodeTransitionsIncrementally(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path application = python.resolve("application.py");
        String originalSource = "# original formatting\nanswer  =  42\n";
        Files.writeString(application, originalSource);
        Files.writeString(python.resolve("other.py"), "value = 1\n");

        compilePython(python, java, output, cache);
        Path vfsSource = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/application.py"
        );
        Path filesList = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/fileslist.txt"
        );
        assertEquals(originalSource, Files.readString(vfsSource));
        assertFalse(hasOutput(output, "application.", ".pyc"));
        assertTrue(Files.readString(filesList).contains("/src/application.py"));
        assertFalse(Files.readString(filesList).contains("/src/__pycache__/application."));

        String transformedSource = """
            import java
            Thread = java.type("java.lang.Thread")
            def yield_thread():
                Thread.yield_()
            """;
        Files.writeString(application, transformedSource);
        compilePython(python, java, output, cache);
        Path mandatoryBytecode = findOutput(output, "application.", ".pyc");
        assertEquals(transformedSource, Files.readString(vfsSource));
        assertTrue(Files.isRegularFile(mandatoryBytecode));
        assertTrue(Files.readString(filesList).contains("/src/__pycache__/application."));

        Files.setLastModifiedTime(vfsSource, UNCHANGED_MARKER);
        Files.setLastModifiedTime(mandatoryBytecode, UNCHANGED_MARKER);
        compilePython(python, java, output, cache);
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(vfsSource));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(mandatoryBytecode));

        Files.writeString(application, originalSource);
        compilePython(python, java, output, cache);
        assertEquals(originalSource, Files.readString(vfsSource));
        assertFalse(hasOutput(output, "application.", ".pyc"));
        assertFalse(Files.readString(filesList).contains("/src/__pycache__/application."));

        Files.writeString(application, transformedSource);
        compilePython(python, java, output, cache);
        assertTrue(hasOutput(output, "application.", ".pyc"));
        Files.delete(application);
        compilePython(python, java, output, cache);
        assertFalse(Files.exists(vfsSource));
        assertFalse(hasOutput(output, "application.", ".pyc"));
        assertFalse(Files.readString(filesList).contains("/src/application.py"));
        assertFalse(Files.readString(filesList).contains("/src/__pycache__/application."));
    }

    @Test
    void recompilesAllContributorsForAggregatingVisitors(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path one = sources.resolve("One.java");
        Files.writeString(one, """
            import io.micronaut.python.compiler.TestAggregate;
            @TestAggregate
            public class One {}
            """);
        Files.writeString(sources.resolve("Two.java"), """
            import io.micronaut.python.compiler.TestAggregate;
            @TestAggregate
            public class Two {}
            """);
        Path unrelated = sources.resolve("Unrelated.java");
        Files.writeString(unrelated, "public class Unrelated {}\n");

        compileJava(sources, output, cache);
        Set<String> aggregatingInputs = decodeStateList(
            state(cache).getProperty("aggregating.inputs")
        );
        assertTrue(aggregatingInputs.contains(one.toRealPath().toString()));
        assertTrue(aggregatingInputs.contains(sources.resolve("Two.java").toRealPath().toString()));
        assertFalse(aggregatingInputs.contains(unrelated.toRealPath().toString()));
        Path twoClass = output.resolve("Two.class");
        Path aggregate = output.resolve("META-INF/pyronaut/aggregating.txt");
        Files.setLastModifiedTime(twoClass, UNCHANGED_MARKER);
        Files.setLastModifiedTime(aggregate, UNCHANGED_MARKER);

        Files.writeString(unrelated, "public class Unrelated { int changed; }\n");
        compileJava(sources, output, cache);
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(twoClass));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(aggregate));
        Path unrelatedClass = output.resolve("Unrelated.class");
        Files.setLastModifiedTime(unrelatedClass, UNCHANGED_MARKER);

        Files.writeString(one, """
            import io.micronaut.python.compiler.TestAggregate;
            @TestAggregate
            public class One { int changed; }
            """);
        compileJava(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(twoClass));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(unrelatedClass));
        String summary = Files.readString(aggregate);
        assertTrue(summary.contains("One"));
        assertTrue(summary.contains("Two"));
        assertFalse(state(cache).getProperty("aggregating.inputs").isBlank());

        Files.delete(sources.resolve("Two.java"));
        compileJava(sources, output, cache);

        summary = Files.readString(output.resolve("META-INF/pyronaut/aggregating.txt"));
        assertTrue(summary.contains("One"));
        assertFalse(summary.contains("Two"));
        assertFalse(Files.exists(twoClass));

        Files.writeString(one, "public class One {}\n");
        compileJava(sources, output, cache);
        assertFalse(Files.exists(output.resolve("META-INF/pyronaut/aggregating.txt")));
    }

    @Test
    void recompilesAllPythonContributorsForAggregatingVisitors(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path one = python.resolve("one.py");
        Files.writeString(one, """
            from io.micronaut.python.compiler import TestAggregate
            @TestAggregate
            class One:
                value: int = 1
            """);
        Files.writeString(python.resolve("two.py"), """
            from io.micronaut.python.compiler import TestAggregate
            @TestAggregate
            class Two:
                value: int = 2
            """);

        compilePython(python, java, output, cache);
        Path aggregate = output.resolve("META-INF/pyronaut/aggregating.txt");
        String summary = Files.readString(aggregate);
        assertTrue(summary.contains("python.One"));
        assertTrue(summary.contains("python.Two"));
        assertTrue(Files.readString(cache.resolve("state.properties"))
            .contains("processor.compatible=true"));

        Files.writeString(one, """
            from io.micronaut.python.compiler import TestAggregate
            @TestAggregate
            class One:
                value: int = 3
            """);
        compilePython(python, java, output, cache);

        summary = Files.readString(aggregate);
        assertTrue(summary.contains("python.One"));
        assertTrue(summary.contains("python.Two"));

        Files.setLastModifiedTime(aggregate, UNCHANGED_MARKER);
        Files.writeString(java.resolve("Helper.java"), "class Helper {}\n");
        compilePython(python, java, output, cache);
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(aggregate));
        summary = Files.readString(aggregate);
        assertTrue(summary.contains("python.One"));
        assertTrue(summary.contains("python.Two"));

        Files.delete(python.resolve("two.py"));
        compilePython(python, java, output, cache);

        summary = Files.readString(aggregate);
        assertTrue(summary.contains("python.One"));
        assertFalse(summary.contains("python.Two"));
    }

    @Test
    void invalidatesForCompilerOptionsCorruptStateAndMissingOutputs(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Files.writeString(sources.resolve("Alpha.java"), "public class Alpha {}\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileJava(sources, output, cache);
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);

        compileJava(sources, output, cache, List.of(), "-parameters");
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));

        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);
        Files.writeString(cache.resolve("state.properties"), "not valid state");
        compileJava(sources, output, cache, List.of(), "-parameters");
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));

        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);
        Files.delete(output.resolve("Alpha.class"));
        compileJava(sources, output, cache, List.of(), "-parameters");
        assertTrue(Files.isRegularFile(output.resolve("Alpha.class")));
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
    }

    @Test
    void invalidatesForClasspathContentChanges(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path classpath = Files.createDirectories(directory.resolve("classpath"));
        Path fingerprintedDependency = classpath.resolve("dependency.version");
        Files.writeString(fingerprintedDependency, "one");
        Files.writeString(sources.resolve("Alpha.java"), "public class Alpha {}\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileJava(sources, output, cache, List.of(classpath));
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);

        Files.writeString(fingerprintedDependency, "two");
        compileJava(sources, output, cache, List.of(classpath));

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
    }

    @Test
    void rejectsStateOutputsOutsideTheTargetDirectory(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha {}\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");
        compileJava(sources, output, cache);

        Path outside = directory.resolve("outside.class");
        Files.writeString(outside, "unmanaged");
        Properties properties = state(cache);
        properties.setProperty(
            "aggregating.outputs",
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "../outside.class".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        );
        try (var stateOutput = Files.newOutputStream(cache.resolve("state.properties"))) {
            properties.store(stateOutput, "unsafe incremental state");
        }
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);
        Files.writeString(alpha, "public class Alpha { int changed; }\n");

        compileJava(sources, output, cache);

        assertTrue(Files.isRegularFile(outside));
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
    }

    @Test
    void invalidatesForProcessorPathBootClasspathAndBytecodeMode(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path processorPath = Files.createDirectories(directory.resolve("processor-path"));
        Path bootClasspath = Files.createDirectories(directory.resolve("boot-classpath"));
        Files.writeString(sources.resolve("Alpha.java"), "public class Alpha {}\n");
        Files.writeString(processorPath.resolve("processor.version"), "one");
        Files.writeString(bootClasspath.resolve("boot.version"), "one");

        IncrementalCompilation initial = incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false
        );
        IncrementalCompilation.Plan initialPlan = initial.plan(false);
        assertTrue(initialPlan.fullRebuild());
        initial.prepareOutput(initialPlan);
        initial.complete(initialPlan, IncrementalCompilationTrace.empty());

        assertTrue(incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false
        ).plan(false).upToDate());

        Files.writeString(processorPath.resolve("processor.version"), "two");
        assertTrue(incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false
        ).plan(false).fullRebuild());
        Files.writeString(processorPath.resolve("processor.version"), "one");

        Files.writeString(bootClasspath.resolve("boot.version"), "two");
        assertTrue(incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false
        ).plan(false).fullRebuild());
        Files.writeString(bootClasspath.resolve("boot.version"), "one");

        assertTrue(incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            true
        ).plan(false).fullRebuild());
    }

    @Test
    void invalidatesForApplicationSettings(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path processorPath = Files.createDirectories(directory.resolve("processor-path"));
        Path bootClasspath = Files.createDirectories(directory.resolve("boot-classpath"));
        Files.writeString(sources.resolve("Alpha.java"), "public class Alpha {}\n");

        IncrementalCompilation initial = incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false,
            "example.one",
            null
        );
        IncrementalCompilation.Plan initialPlan = initial.plan(false);
        initial.prepareOutput(initialPlan);
        initial.complete(initialPlan, IncrementalCompilationTrace.empty());

        assertTrue(incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false,
            "example.two",
            null
        ).plan(false).fullRebuild());
        assertTrue(incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            false,
            "example.one",
            "Alpha"
        ).plan(false).fullRebuild());
    }

    @Test
    void invalidatesStateAfterFailedCompilationAndRepairsOnNextRun(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha {}\n");
        compileJava(sources, output, cache);

        Files.writeString(alpha, "public class Alpha { syntax error }\n");
        assertThrows(RuntimeException.class, () -> compileJava(sources, output, cache));
        assertFalse(Files.exists(cache.resolve("state.properties")));

        Files.writeString(alpha, "public class Alpha { int repaired; }\n");
        compileJava(sources, output, cache);
        assertTrue(Files.isRegularFile(output.resolve("Alpha.class")));
        assertTrue(Files.isRegularFile(cache.resolve("state.properties")));
    }

    @Test
    void recompilesJavaConstantAndTransitiveDependents(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path constants = sources.resolve("Constants.java");
        Files.writeString(constants, "class Constants { static final int VALUE = 1; }\n");
        Files.writeString(
            sources.resolve("Consumer.java"),
            "class Consumer { int value = Constants.VALUE; }\n"
        );
        Files.writeString(
            sources.resolve("Transitive.java"),
            "class Transitive { Consumer consumer = new Consumer(); }\n"
        );
        compileJava(sources, output, cache);
        Path consumerClass = output.resolve("Consumer.class");
        Path transitiveClass = output.resolve("Transitive.class");
        Files.setLastModifiedTime(consumerClass, UNCHANGED_MARKER);
        Files.setLastModifiedTime(transitiveClass, UNCHANGED_MARKER);

        Files.writeString(constants, "class Constants { static final int VALUE = 2; }\n");
        compileJava(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerClass));
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(transitiveClass));
    }

    @Test
    void recompilesJavaAnnotationDependents(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path annotation = sources.resolve("Marker.java");
        Files.writeString(annotation, "@interface Marker { int value() default 1; }\n");
        Files.writeString(sources.resolve("Annotated.java"), "@Marker class Annotated {}\n");
        compileJava(sources, output, cache);
        Path annotatedClass = output.resolve("Annotated.class");
        Files.setLastModifiedTime(annotatedClass, UNCHANGED_MARKER);

        Files.writeString(annotation, "@interface Marker { int value() default 2; }\n");
        compileJava(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(annotatedClass));
    }

    @Test
    void recompilesDependentsOfPackagePrivateMembers(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path api = sources.resolve("PackageApi.java");
        Files.writeString(api, """
            class PackageApi {
                int value() { return 1; }
            }
            """);
        Files.writeString(sources.resolve("PackageConsumer.java"), """
            class PackageConsumer {
                int value() { return new PackageApi().value(); }
            }
            """);
        compileJava(sources, output, cache);
        Path consumerClass = output.resolve("PackageConsumer.class");
        Files.setLastModifiedTime(consumerClass, UNCHANGED_MARKER);

        Files.writeString(api, """
            class PackageApi {
                int value() { return 2; }
            }
            """);
        compileJava(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerClass));
    }

    @Test
    void fallsBackToFullCompilationAfterIsolatingProcessorContractViolation(
        @TempDir Path directory
    ) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha {}\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileWithContractViolatingProcessor(sources, output, cache);
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);

        Files.writeString(alpha, "public class Alpha { int changed; }\n");
        compileWithContractViolatingProcessor(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
        assertTrue(Files.isRegularFile(output.resolve("META-INF/contract-violation.txt")));
    }

    @Test
    void immediatelyRepairsAfterAProcessorStartsViolatingItsIsolatingContract(
        @TempDir Path directory
    ) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, """
            import io.micronaut.python.compiler.TestConditionalIsolate;
            @TestConditionalIsolate(valid = true)
            public class Alpha {}
            """);
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileJava(sources, output, cache);
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);
        Files.writeString(output.resolve("stale-output.txt"), "stale");

        Files.writeString(alpha, """
            import io.micronaut.python.compiler.TestConditionalIsolate;
            @TestConditionalIsolate(valid = false)
            public class Alpha { int changed; }
            """);
        compileJava(sources, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
        assertFalse(Files.exists(output.resolve("stale-output.txt")));
        assertFalse(Boolean.parseBoolean(state(cache).getProperty("processor.compatible")));
    }

    @Test
    void fallsBackToFullCompilationForNonIncrementalProcessor(@TempDir Path directory) throws Exception {
        Path sources = Files.createDirectories(directory.resolve("sources"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = sources.resolve("Alpha.java");
        Files.writeString(alpha, "public class Alpha {}\n");
        Files.writeString(sources.resolve("Beta.java"), "public class Beta {}\n");

        compileWithProcessor(sources, output, cache, new TestNonIncrementalProcessor());
        Path betaClass = output.resolve("Beta.class");
        Files.setLastModifiedTime(betaClass, UNCHANGED_MARKER);
        Files.writeString(output.resolve("stale-output.txt"), "stale");

        Files.writeString(alpha, "public class Alpha { int changed; }\n");
        compileWithProcessor(sources, output, cache, new TestNonIncrementalProcessor());

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaClass));
        assertTrue(Files.readString(cache.resolve("state.properties"))
            .contains("processor.compatible=false"));
        assertFalse(Files.exists(output.resolve("stale-output.txt")));
    }

    @Test
    void removesDeletedPythonOutputsAndReprocessesImportDependents(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path dependency = python.resolve("dependency.py");
        Path consumer = python.resolve("consumer.py");
        Files.writeString(dependency, "class Dependency:\n    value: int = 1\n");
        Files.writeString(
            consumer,
            "from dependency import Dependency\nclass Consumer:\n    value: Dependency\n"
        );
        compilePython(python, java, output, cache);
        Path consumerVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/consumer.py"
        );
        Files.setLastModifiedTime(consumerVfs, UNCHANGED_MARKER);

        Files.writeString(dependency, "class Dependency:\n    value: int = 2\n");
        compilePython(python, java, output, cache);
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerVfs));

        Files.delete(dependency);
        compilePython(python, java, output, cache);
        assertFalse(Files.exists(output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/dependency.py"
        )));
    }

    @Test
    void reprocessesAllPythonSourcesForDynamicRelationships(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, "class Alpha:\n    value: int = 1\n");
        Files.writeString(python.resolve("dynamic.py"), """
            class Dynamic:
                value: object = getattr(object(), "value", None)
            """);
        compilePython(python, java, output, cache);
        Path dynamicVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/dynamic.py"
        );
        Files.setLastModifiedTime(dynamicVfs, UNCHANGED_MARKER);

        Files.writeString(alpha, "class Alpha:\n    value: int = 2\n");
        compilePython(python, java, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(dynamicVfs));
    }

    @Test
    void optimisticModeDoesNotExpandDynamicPythonRelationships(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, "class Alpha:\n    value: int = 1\n");
        Files.writeString(python.resolve("dynamic.py"), """
            class Dynamic:
                value: object = getattr(object(), "value", None)
            """);
        compilePython(python, java, output, cache, PythonIncrementalMode.OPTIMISTIC);
        Path dynamicVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/dynamic.py"
        );
        Files.setLastModifiedTime(dynamicVfs, UNCHANGED_MARKER);

        Files.writeString(alpha, "class Alpha:\n    value: int = 2\n");
        compilePython(python, java, output, cache, PythonIncrementalMode.OPTIMISTIC);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(dynamicVfs));
    }

    @Test
    void optimisticModeDoesNotReprocessImportDependentsForBodyOnlyChanges(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path dependency = python.resolve("dependency.py");
        Files.writeString(
            dependency,
            "class Dependency:\n    def value(self) -> int:\n        result = 1\n        return result\n"
        );
        Files.writeString(
            python.resolve("consumer.py"),
            "from dependency import Dependency\nclass Consumer:\n    value: Dependency\n"
        );
        compilePython(python, java, output, cache, PythonIncrementalMode.OPTIMISTIC);
        Path consumerVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/consumer.py"
        );
        Files.setLastModifiedTime(consumerVfs, UNCHANGED_MARKER);

        Files.writeString(
            dependency,
            "class Dependency:\n    def value(self) -> int:\n        result = 2\n        return result\n"
        );
        compilePython(python, java, output, cache, PythonIncrementalMode.OPTIMISTIC);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerVfs));
    }

    @Test
    void optimisticModeReprocessesImportDependentsForDeclarationChanges(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path dependency = python.resolve("dependency.py");
        Files.writeString(dependency, "class Dependency:\n    value: int = 1\n");
        Files.writeString(
            python.resolve("consumer.py"),
            "from dependency import Dependency\nclass Consumer:\n    value: Dependency\n"
        );
        compilePython(python, java, output, cache, PythonIncrementalMode.OPTIMISTIC);
        Path consumerVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/consumer.py"
        );
        Files.setLastModifiedTime(consumerVfs, UNCHANGED_MARKER);

        Files.writeString(dependency, "class Dependency:\n    value: str = \"changed\"\n");
        compilePython(python, java, output, cache, PythonIncrementalMode.OPTIMISTIC);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerVfs));
    }

    @Test
    void doesNotReprocessDynamicPythonSourcesForUnrelatedJavaChanges(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Files.writeString(python.resolve("dynamic.py"), """
            class Dynamic:
                value: object = getattr(object(), "value", None)
            """);
        Path unrelatedJava = java.resolve("Unrelated.java");
        Files.writeString(unrelatedJava, "class Unrelated { int value = 1; }\n");
        compilePython(python, java, output, cache);
        Path dynamicVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/dynamic.py"
        );
        Files.setLastModifiedTime(dynamicVfs, UNCHANGED_MARKER);

        Files.writeString(unrelatedJava, "class Unrelated { int value = 2; }\n");
        compilePython(python, java, output, cache);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(dynamicVfs));
    }

    @Test
    void reprocessesAllPythonSourcesForUnresolvedRelativeImports(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python/pkg"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, "class Alpha:\n    value: int = 1\n");
        Files.writeString(python.resolve("consumer.py"), """
            from .missing import Missing
            class Consumer:
                value: int = 1
            """);
        compilePython(directory.resolve("python"), java, output, cache);
        Path consumerVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/pkg/consumer.py"
        );
        Files.setLastModifiedTime(consumerVfs, UNCHANGED_MARKER);

        Files.writeString(alpha, "class Alpha:\n    value: int = 2\n");
        compilePython(directory.resolve("python"), java, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerVfs));
    }

    @Test
    void reprocessesAllPythonSourcesForRelativeStarImports(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python/pkg"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, "class Alpha:\n    value: int = 1\n");
        Files.writeString(python.resolve("symbols.py"), "VALUE = 1\n");
        Files.writeString(python.resolve("consumer.py"), """
            from .symbols import *
            class Consumer:
                value: int = VALUE
            """);
        compilePython(directory.resolve("python"), java, output, cache);
        Path consumerVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/pkg/consumer.py"
        );
        Files.setLastModifiedTime(consumerVfs, UNCHANGED_MARKER);

        Files.writeString(alpha, "class Alpha:\n    value: int = 2\n");
        compilePython(directory.resolve("python"), java, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerVfs));
    }

    @Test
    void preservesUnchangedSharedPythonPackageOutputs(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python/pkg"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, "class Alpha:\n    value: int = 1\n");
        compilePython(directory.resolve("python"), java, output, cache);
        Path initializer = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/pkg/__init__.py"
        );
        Path filesList = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/fileslist.txt"
        );
        List<String> cleanEntries = Files.readAllLines(filesList);
        assertEquals(cleanEntries.stream().sorted().toList(), cleanEntries);
        Files.setLastModifiedTime(initializer, UNCHANGED_MARKER);
        Files.setLastModifiedTime(filesList, UNCHANGED_MARKER);

        Files.writeString(alpha, "class Alpha:\n    value: int = 2\n");
        compilePython(directory.resolve("python"), java, output, cache);

        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(initializer));
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(filesList));
        assertTrue(Files.readString(filesList).contains("/pkg/__init__.py"));
        assertTrue(decodeStateList(state(cache).getProperty("python.aggregating.outputs")).stream()
            .anyMatch(outputPath -> outputPath.endsWith("/pkg/__init__.py")));
    }

    @Test
    void regeneratesSharedPythonPackageOutputsAfterStructuralChange(@TempDir Path directory)
        throws Exception {
        Path python = Files.createDirectories(directory.resolve("python/pkg"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path alpha = python.resolve("alpha.py");
        Files.writeString(alpha, "class Alpha:\n    value: int = 1\n");
        Files.writeString(python.resolve("beta.py"), "class Beta:\n    value: int = 2\n");
        compilePython(directory.resolve("python"), java, output, cache);
        Path initializer = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/pkg/__init__.py"
        );

        Files.writeString(alpha, """
            class Alpha:
                value: int = 1
            class Added:
                value: int = 3
            """);
        compilePython(directory.resolve("python"), java, output, cache);

        String content = Files.readString(initializer);
        assertTrue(content.contains("from .alpha import Added"));
        assertTrue(content.contains("from .beta import Beta"));
    }

    @Test
    void propagatesDependenciesAcrossJavaAndPython(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java/example"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path javaDependency = java.resolve("SharedJava.java");
        Files.writeString(javaDependency, """
            package example;
            public class SharedJava { public int value() { return 1; } }
            """);
        Files.writeString(python.resolve("consumer.py"), """
            from example import SharedJava
            class Consumer:
                value: SharedJava
            """);
        Files.writeString(python.resolve("dynamic.py"), """
            class Dynamic:
                value: object = getattr(object(), "value", None)
            """);

        compilePython(python, directory.resolve("java"), output, cache);
        Path consumerVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/consumer.py"
        );
        Path dynamicVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/dynamic.py"
        );
        Files.setLastModifiedTime(consumerVfs, UNCHANGED_MARKER);
        Files.setLastModifiedTime(dynamicVfs, UNCHANGED_MARKER);

        Files.writeString(javaDependency, """
            package example;
            public class SharedJava { public int value() { return 2; } }
            """);
        compilePython(python, directory.resolve("java"), output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerVfs));
        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(dynamicVfs));
    }

    @Test
    void recompilesJavaSourcesThatReferenceGeneratedPythonTypes(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path pythonType = python.resolve("alpha.py");
        Files.writeString(pythonType, """
            class Alpha:
                value: int = 1
            """);
        Files.writeString(java.resolve("Consumer.java"), """
            class Consumer {
                python.Alpha value;
            }
            """);

        compilePython(python, java, output, cache);
        Path consumerClass = output.resolve("Consumer.class");
        Files.setLastModifiedTime(consumerClass, UNCHANGED_MARKER);

        Files.writeString(pythonType, """
            class Alpha:
                value: int = 2
            """);
        compilePython(python, java, output, cache);

        assertNotEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(consumerClass));
    }

    @Test
    void changingApplicationSourceDoesNotDeleteUnchangedPythonOutputs(@TempDir Path directory) throws Exception {
        Path python = Files.createDirectories(directory.resolve("python"));
        Path java = Files.createDirectories(directory.resolve("java/example"));
        Path javaRoot = directory.resolve("java");
        Path output = directory.resolve("classes");
        Path cache = directory.resolve("incremental");
        Path application = java.resolve("Application.java");
        String escapedPython = python.toString().replace("\\", "\\\\");
        Files.writeString(application, """
            package example;
            import io.micronaut.context.python.annotation.PythonApplication;
            @PythonApplication(src = "%s")
            public class Application {}
            """.formatted(escapedPython));
        Files.writeString(python.resolve("alpha.py"), "class Alpha:\n    value: int = 1\n");
        Files.writeString(python.resolve("beta.py"), "class Beta:\n    value: int = 2\n");

        compilePythonWithApplication(
            python,
            javaRoot,
            output,
            cache,
            "example.Application"
        );
        assertEquals(
            "true",
            state(cache).getProperty("processor.compatible"),
            Files.readString(cache.resolve("state.properties"))
        );
        Path betaVfs = output.resolve(
            "META-INF/GRAALPY-VFS/micronaut-application/src/beta.py"
        );
        Files.setLastModifiedTime(betaVfs, UNCHANGED_MARKER);

        Files.writeString(application, """
            package example;
            import io.micronaut.context.python.annotation.PythonApplication;
            @PythonApplication(src = "%s")
            public class Application { int changed; }
            """.formatted(escapedPython));
        compilePythonWithApplication(
            python,
            javaRoot,
            output,
            cache,
            "example.Application"
        );

        assertTrue(Files.isRegularFile(betaVfs));
        assertEquals(UNCHANGED_MARKER, Files.getLastModifiedTime(betaVfs));
    }

    private static void compileJava(Path sources, Path output, Path cache) {
        compileJava(sources, output, cache, List.of());
    }

    private static void compileJava(Path sources,
                                    Path output,
                                    Path cache,
                                    List<Path> classpath,
                                    String... options) {
        PyronautCompiler.builder()
            .javaSrc(sources.toString())
            .targetDir(output.toFile())
            .classpath(classpath.stream().map(Path::toFile).toList())
            .annotationProcessorPath(classpath.stream().map(Path::toFile).toList())
            .options(List.of(options))
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .build()
            .compile();
    }

    private static void compileJavaWithPlanCallback(
        Path sources,
        Path output,
        Path cache,
        List<PyronautCompiler.IncrementalCompilationPlan> plans) {
        PyronautCompiler.builder()
            .javaSrc(sources.toString())
            .targetDir(output.toFile())
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .incrementalCompilationPlanCallback(plans::add)
            .build()
            .compile();
    }

    private static void compilePython(Path python, Path java, Path output, Path cache) {
        compilePython(python, java, output, cache, false);
    }

    private static void compilePython(Path python,
                                      Path java,
                                      Path output,
                                      Path cache,
                                      PythonProcessingSession session) {
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .targetDir(output.toFile())
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .pythonProcessingSession(session)
            .build()
            .compile();
    }

    private static void assertPythonControllerAnnotations(Path python,
                                                          Path java,
                                                          Path output,
                                                          Path httpClasspath,
                                                          PythonProcessingSession session) {
        List<Boolean> controllerAnnotations = new ArrayList<>();
        List<Boolean> routeAnnotations = new ArrayList<>();
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .targetDir(output.toFile())
            .classpath(List.of(httpClasspath.toFile()))
            .annotationProcessorPath(List.of(httpClasspath.toFile()))
            .pythonProcessingSession(session)
            .classElementCallback(type -> {
                if (type.getName().endsWith("Example")) {
                    controllerAnnotations.add(type.isAnnotationPresent("io.micronaut.http.annotation.Controller"));
                    type.getEnclosedElements(io.micronaut.inject.ast.ElementQuery.ALL_METHODS).stream()
                        .filter(method -> method.getName().equals("index"))
                        .forEach(method -> routeAnnotations.add(method.isAnnotationPresent("io.micronaut.http.annotation.Get")));
                }
            })
            .build()
            .compile();
        assertEquals(List.of(true), controllerAnnotations);
        assertEquals(List.of(true), routeAnnotations);
    }

    private static void compilePython(Path python,
                                      Path java,
                                      Path output,
                                      Path cache,
                                      PythonIncrementalMode pythonIncrementalMode) {
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .targetDir(output.toFile())
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .pythonIncrementalMode(pythonIncrementalMode)
            .build()
            .compile();
    }

    private static void compilePython(Path python,
                                      Path java,
                                      Path output,
                                      Path cache,
                                      boolean bytecode) {
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .targetDir(output.toFile())
            .compilePythonBytecode(bytecode)
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .build()
            .compile();
    }

    private static void compilePythonWithApplication(Path python,
                                                     Path java,
                                                     Path output,
                                                     Path cache,
                                                     String applicationClass) {
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .applicationClass(applicationClass)
            .targetDir(output.toFile())
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .build()
            .compile();
    }

    private static IncrementalCompilation incrementalState(Path sources,
                                                           Path output,
                                                           Path cache,
                                                           Path processorPath,
                                                           Path bootClasspath,
                                                           boolean bytecode) {
        return incrementalState(
            sources,
            output,
            cache,
            processorPath,
            bootClasspath,
            bytecode,
            "pyronaut_application",
            null
        );
    }

    private static IncrementalCompilation incrementalState(Path sources,
                                                           Path output,
                                                           Path cache,
                                                           Path processorPath,
                                                           Path bootClasspath,
                                                           boolean bytecode,
                                                           String packageName,
                                                           String applicationClass) {
        return new IncrementalCompilation(
            sources.toString(),
            null,
            null,
            packageName,
            applicationClass,
            output.toFile(),
            cache.toFile(),
            List.of(),
            List.of(bootClasspath.toFile()),
            List.of(processorPath.toFile()),
            List.of("-Amicronaut.processing.incremental=true"),
            bytecode,
            List.of(),
            List.of(),
            PythonIncrementalMode.CONSERVATIVE
        );
    }

    private static Properties state(Path cache) throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(cache.resolve("state.properties"))) {
            properties.load(input);
        }
        return properties;
    }

    private static Set<String> decodeStateList(String value) {
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(encoded -> new String(
                java.util.Base64.getUrlDecoder().decode(encoded),
                java.nio.charset.StandardCharsets.UTF_8
            ))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void compileWithContractViolatingProcessor(Path sources,
                                                              Path output,
                                                              Path cache) {
        compileWithProcessor(sources, output, cache, new TestContractViolatingProcessor());
    }

    private static void compileWithProcessor(Path sources,
                                             Path output,
                                             Path cache,
                                             AbstractProcessor processor) {
        PyronautCompiler.builder()
            .javaSrc(sources.toString())
            .targetDir(output.toFile())
            .annotationProcessors(List.of(processor))
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .build()
            .compile();
    }

    private static Path findOutput(Path output, String nameToken) throws Exception {
        return findOutput(output, nameToken, "");
    }

    private static Path findOutput(Path output, String nameToken, String suffix) throws Exception {
        try (var paths = Files.walk(output)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(nameToken))
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow();
        }
    }

    private static boolean hasOutput(Path output, String nameToken, String suffix) throws Exception {
        try (var paths = Files.walk(output)) {
            return paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .anyMatch(name -> name.contains(nameToken) && name.endsWith(suffix));
        }
    }

    @SupportedAnnotationTypes("*")
    private static final class SystemPropertyMutatingProcessor extends AbstractProcessor {
        private final String property;

        private SystemPropertyMutatingProcessor(String property) {
            this.property = property;
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            System.setProperty(property, "changed");
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }
    }

    @SupportedAnnotationTypes("*")
    private static final class TestNonIncrementalProcessor extends AbstractProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }
    }
}
