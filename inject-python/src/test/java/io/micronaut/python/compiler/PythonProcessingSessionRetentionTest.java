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

import com.sun.management.UnixOperatingSystemMXBean;
import io.micronaut.openapi.visitor.Utils;
import io.micronaut.python.processing.PythonAstParser;
import io.micronaut.python.processing.PythonProcessingSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session keeps the class loader of the processors and the GraalPy context from one incremental compilation to the
 * next, for as long as what they were loaded from does not change, and what it keeps does not change what is compiled.
 */
final class PythonProcessingSessionRetentionTest {
    private static final String SOURCE = "META-INF/GRAALPY-VFS/micronaut-application/src/example.py";

    @TempDir
    Path directory;

    @Test
    void anIncrementalCompilationKeepsTheClassLoaderAndTheContext() throws IOException {
        Project project = project();
        Path jar = writeJar(directory.resolve("library.jar"), "unchanged");
        List<ClassLoader> classLoaders = new ArrayList<>();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, List.of(jar.toFile()), null, classLoaders);
            PythonAstParser parser = session.parser(getClass().getClassLoader(), false);

            for (int value = 2; value < 5; value++) {
                project.write("class Example:\n    value: int = " + value + "\n");
                project.compile(session, List.of(jar.toFile()), null, classLoaders);
                assertSame(parser, session.parser(getClass().getClassLoader(), false), "the GraalPy context stayed warm");
                assertTrue(Files.readString(project.output.resolve(SOURCE)).contains("value: int = " + value));
            }
        }
        assertEquals(4, classLoaders.size());
        assertEquals(1, classLoaders.stream().distinct().count(), "one class loader: " + classLoaders);
    }

    @Test
    void theClassLoaderOfASessionDoesNotHoldTheOutput() throws IOException {
        Project project = project();
        List<ClassLoader> classLoaders = new ArrayList<>();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, List.of(), null, classLoaders);
            project.write("class Example:\n    value: int = 2\n");
            project.compile(session, List.of(), null, classLoaders);
            assertTrue(Files.exists(project.output.resolve(SOURCE)));
            assertNull(classLoaders.getLast().getResource(SOURCE), "a class it loaded from there would stay as it was");
        }
    }

    @Test
    void aChangedJarOnTheClasspathStartsOver() throws IOException {
        Project project = project();
        Path jar = writeJar(directory.resolve("library.jar"), "first");
        List<ClassLoader> classLoaders = new ArrayList<>();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, List.of(jar.toFile()), null, classLoaders);
            PythonAstParser parser = session.parser(getClass().getClassLoader(), false);

            writeJar(jar, "the second, which is longer");
            project.write("class Example:\n    value: int = 2\n");
            project.compile(session, List.of(jar.toFile()), null, classLoaders);

            assertNotSame(parser, session.parser(getClass().getClassLoader(), false));
            assertEquals(2, classLoaders.stream().distinct().count());
        }
    }

    @Test
    void aChangedProcessorPathStartsOver() throws IOException {
        Project project = project();
        Path first = writeJar(directory.resolve("first.jar"), "first");
        Path second = writeJar(directory.resolve("second.jar"), "second");
        List<ClassLoader> classLoaders = new ArrayList<>();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, List.of(), List.of(first.toFile()), classLoaders);
            PythonAstParser parser = session.parser(getClass().getClassLoader(), false);

            project.write("class Example:\n    value: int = 2\n");
            project.compile(session, List.of(), List.of(first.toFile(), second.toFile()), classLoaders);

            assertNotSame(parser, session.parser(getClass().getClassLoader(), false));
            assertEquals(2, classLoaders.stream().distinct().count());
        }
    }

    @Test
    void aChangedVisitorInAClassesDirectoryRunsInTheNextCompilation() throws IOException {
        Project project = project();
        Path processor = Files.createDirectories(directory.resolve("processor-classes"));
        writeVisitor(processor, "first");
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, List.of(), List.of(processor.toFile()), new ArrayList<>());
            assertEquals("first", Files.readString(project.output.resolve("META-INF/retention-visitor.txt")));

            writeVisitor(processor, "second");
            project.write("class Example:\n    value: int = 2\n");
            project.compile(session, List.of(), List.of(processor.toFile()), new ArrayList<>());
            assertEquals("second", Files.readString(project.output.resolve("META-INF/retention-visitor.txt")));
        }
    }

    @Test
    void aDirectoryRebuiltWithTheSameFilesKeepsTheSession() throws IOException {
        // the classes another tool wrote into a shared class output, which micronaut-dev links into a new directory
        // for every compilation
        Path classes = Files.createDirectories(directory.resolve("foreign/pkg"));
        Path file = Files.writeString(classes.resolve("Foreign.class"), "first");
        FileTime written = Files.getLastModifiedTime(file);
        List<File> classpath = List.of(directory.resolve("foreign").toFile());
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            ClassLoader first = session.classLoader(classpath, this::newClassLoader);

            Files.delete(file);
            Files.delete(classes);
            Files.createDirectories(classes);
            Files.setLastModifiedTime(Files.writeString(classes.resolve("Foreign.class"), "first"), written);
            assertSame(first, session.classLoader(classpath, this::newClassLoader));

            Files.setLastModifiedTime(file, FileTime.fromMillis(written.toMillis() + 5_000));
            assertNotSame(first, session.classLoader(classpath, this::newClassLoader));
        }
    }

    @Test
    void theStateOfTheProcessorsIsReleasedAfterEveryCompilation() throws IOException {
        Project project = project();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            int before = Utils.CLEANED.get();
            project.compile(session, List.of(), null, new ArrayList<>());
            assertEquals(before + 1, Utils.CLEANED.get());

            project.write("class Example:\n    value: int = 2\n");
            project.compile(session, List.of(), null, new ArrayList<>());
            assertEquals(before + 2, Utils.CLEANED.get());

            // and after one that failed, whose elements it would hold just the same
            project.write("class Example:\n    value: int = \n");
            assertThrows(RuntimeException.class, () -> project.compile(session, List.of(), null, new ArrayList<>()));
            assertEquals(before + 3, Utils.CLEANED.get());
        }
    }

    @Test
    void theContextIsKeptThroughAnEditThatDoesNotCompile() throws IOException {
        Project project = project();
        List<ClassLoader> classLoaders = new ArrayList<>();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, List.of(), null, classLoaders);
            PythonAstParser parser = session.parser(getClass().getClassLoader(), false);

            project.write("class Example:\n    value: int = \n");
            assertThrows(RuntimeException.class, () -> project.compile(session, List.of(), null, new ArrayList<>()));
            assertSame(parser, session.parser(getClass().getClassLoader(), false));

            project.write("class Example:\n    value: int = 3\n");
            project.compile(session, List.of(), null, classLoaders);
            assertSame(parser, session.parser(getClass().getClassLoader(), false));
            assertTrue(Files.readString(project.output.resolve(SOURCE)).contains("value: int = 3"));
        }
    }

    @Test
    void aCompilationThatDidNotRunToTheEndStartsOver(@TempDir Path other) throws IOException {
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            List<File> classpath = List.of(writeJar(other.resolve("library.jar"), "unchanged").toFile());
            ClassLoader first = session.classLoader(classpath, this::newClassLoader);
            session.compilationFinished(true);
            assertSame(first, session.classLoader(classpath, this::newClassLoader));
            session.compilationFinished(false);
            assertNotSame(first, session.classLoader(classpath, this::newClassLoader));
        }
    }

    @Test
    void compilationsDoNotLeaveTheJarsOfTheClasspathOpen() throws IOException {
        OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();
        if (!(system instanceof UnixOperatingSystemMXBean unix)) {
            return;
        }
        Project project = project();
        List<File> classpath = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            classpath.add(writeJar(directory.resolve("library-" + i + ".jar"), "jar " + i).toFile());
        }
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            project.compile(session, classpath, null, new ArrayList<>());
            project.write("class Example:\n    value: int = 2\n");
            project.compile(session, classpath, null, new ArrayList<>());
            long before = unix.getOpenFileDescriptorCount();
            for (int value = 3; value < 9; value++) {
                project.write("class Example:\n    value: int = " + value + "\n");
                project.compile(session, classpath, null, new ArrayList<>());
            }
            long growth = unix.getOpenFileDescriptorCount() - before;
            // each compilation with its file manager left open adds the jars of the class path; a little noise is allowed
            assertTrue(growth < classpath.size(), "six compilations left " + growth + " more files open");
        }
    }

    @Test
    void repeatedCompilationsGiveWhatACleanBuildGives() throws IOException {
        Path python = Files.createDirectories(directory.resolve("python/app"));
        Path root = python.getParent();
        Path java = Files.createDirectories(directory.resolve("java"));
        Path output = directory.resolve("retained/classes");
        Path cache = directory.resolve("retained/cache");
        Files.writeString(python.resolve("models.py"), """
            from dataclasses import dataclass

            from micronaut.core.annotation import Introspected


            @Introspected
            @dataclass
            class Greeting:
                message: str
            """);
        Path other = Files.writeString(python.resolve("other.py"), """
            from jakarta.inject import Singleton


            @Singleton
            class Other:
                def name(self) -> str:
                    return "other"
            """);
        Path controller = Files.writeString(python.resolve("controller.py"), """
            from micronaut.http.annotation import Controller, Get

            from app.models import Greeting


            @Controller("/api")
            class Api:
                @Get("/hello")
                def hello(self) -> Greeting:
                    return Greeting(message="first")
            """);
        Path models = python.resolve("models.py");
        List<Edit> edits = List.of(
            new Edit("a body", () -> replace(controller, "\"first\"", "\"second\"")),
            new Edit("a route added", () -> append(controller, """

                    @Get("/added")
                    def added(self) -> Greeting:
                        return Greeting(message="added")
                """)),
            new Edit("the arguments of a decorator", () -> replace(controller, "@Get(\"/added\")", "@Get(value=\"/renamed\", produces=\"text/plain\")")),
            new Edit("a route removed", () -> truncate(controller, "\n    @Get(value=\"/renamed\"")),
            new Edit("a model added", () -> append(models, """


                @Introspected
                @dataclass
                class Added:
                    value: int
                """)),
            new Edit("a model removed", () -> truncate(models, "\n\n@Introspected\n@dataclass\nclass Added")),
            new Edit("a model renamed", () -> {
                replace(models, "Greeting", "Salute");
                replace(controller, "Greeting", "Salute");
            }),
            new Edit("a module deleted", () -> Files.delete(other)),
            new Edit("an edit that fails", () -> replace(controller, "\"second\"", "\"broken")),
            new Edit("its fix", () -> replace(controller, "\"broken", "\"fixed\"")),
            new Edit("a module added", () -> Files.writeString(python.resolve("added.py"), """
                from jakarta.inject import Singleton


                @Singleton
                class Added:
                    pass
                """))
        );
        Path processor = writeJar(directory.resolve("processor.jar"), "unchanged");
        List<File> processorPath = List.of(processor.toFile());
        List<ClassLoader> classLoaders = new ArrayList<>();
        try (PythonProcessingSession session = new PythonProcessingSession()) {
            compile(root, java, output, cache, processorPath, session, classLoaders);
            PythonAstParser parser = session.parser(getClass().getClassLoader(), false);
            int clean = 0;
            for (Edit edit : edits) {
                edit.change().run();
                boolean fails = edit.name().equals("an edit that fails");
                if (fails) {
                    assertThrows(RuntimeException.class, () -> compile(root, java, output, cache, processorPath, session, new ArrayList<>()), edit.name());
                    continue;
                }
                compile(root, java, output, cache, processorPath, session, classLoaders);
                assertSame(parser, session.parser(getClass().getClassLoader(), false), "the context was kept after " + edit.name());
                clean++;
                Path cleanOutput = directory.resolve("clean-" + clean + "/classes");
                try (PythonProcessingSession fresh = new PythonProcessingSession()) {
                    compile(root, java, cleanOutput, directory.resolve("clean-" + clean + "/cache"), processorPath, fresh, new ArrayList<>());
                }
                assertSameOutput(cleanOutput, output, edit.name());
            }
        }
        assertEquals(1, classLoaders.stream().distinct().count());
    }

    private static void compile(Path python,
                                Path java,
                                Path output,
                                Path cache,
                                List<File> processorPath,
                                PythonProcessingSession session,
                                List<ClassLoader> classLoaders) {
        PyronautCompiler.builder()
            .pythonSrc(python.toString())
            .javaSrc(java.toString())
            .targetDir(output.toFile())
            .incremental(true)
            .incrementalCacheDirectory(cache.toFile())
            .annotationProcessorPath(processorPath)
            .pythonProcessingSession(session)
            .classElementCallback(type -> {
                if (classLoaders.isEmpty() || classLoaders.getLast() != Thread.currentThread().getContextClassLoader()) {
                    classLoaders.add(Thread.currentThread().getContextClassLoader());
                }
            })
            .build()
            .compile();
    }

    private static void assertSameOutput(Path expected, Path actual, String after) throws IOException {
        Set<String> expectedFiles = files(expected);
        assertEquals(expectedFiles, files(actual), "the files after " + after);
        for (String file : expectedFiles) {
            if (file.endsWith("fileslist.txt")) {
                assertEquals(listedFiles(expected, file), listedFiles(actual, file), file + " after " + after);
            } else {
                assertEquals(-1L, Files.mismatch(expected.resolve(file), actual.resolve(file)), file + " after " + after);
            }
        }
    }

    /**
     * The entries of a VFS file list whose files exist: an incremental compilation, with or without a session, rebuilds
     * the list before it deletes the manifests it replaced, which are named by the hash of their content.
     */
    private static List<String> listedFiles(Path output, String list) throws IOException {
        return Files.readAllLines(output.resolve(list)).stream()
            .filter(entry -> Files.exists(output.resolve(entry.substring(1))))
            .toList();
    }

    private static Set<String> files(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                .map(file -> directory.relativize(file).toString())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static void replace(Path file, String from, String to) throws IOException {
        String content = Files.readString(file);
        assertTrue(content.contains(from), from);
        Files.writeString(file, content.replace(from, to));
    }

    private static void append(Path file, String text) throws IOException {
        Files.writeString(file, Files.readString(file) + text);
    }

    private static void truncate(Path file, String from) throws IOException {
        String content = Files.readString(file);
        int start = content.indexOf(from);
        assertTrue(start > 0, from);
        Files.writeString(file, content.substring(0, start) + "\n");
    }

    private Project project() throws IOException {
        Project project = new Project(
            Files.createDirectories(directory.resolve("python")),
            Files.createDirectories(directory.resolve("java")),
            directory.resolve("classes"),
            directory.resolve("cache")
        );
        project.write("class Example:\n    value: int = 1\n");
        return project;
    }

    private ClassLoader newClassLoader() {
        return new java.net.URLClassLoader(new java.net.URL[0], getClass().getClassLoader());
    }

    private static Path writeJar(Path jar, String content) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("marker.txt"));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    /**
     * Compiles a visitor that writes the given text as a resource into the output, with its service entry.
     */
    private void writeVisitor(Path classes, String text) throws IOException {
        Path sources = Files.createDirectories(directory.resolve("visitor-src-" + text + "/retention"));
        Path source = Files.writeString(sources.resolve("RetentionVisitor.java"), """
            package retention;

            import io.micronaut.inject.ast.ClassElement;
            import io.micronaut.inject.visitor.TypeElementVisitor;
            import io.micronaut.inject.visitor.VisitorContext;
            import java.io.IOException;
            import java.io.Writer;

            public class RetentionVisitor implements TypeElementVisitor<Object, Object> {
                @Override
                public void visitClass(ClassElement element, VisitorContext context) {
                    context.visitMetaInfFile("retention-visitor.txt", element).ifPresent(file -> {
                        try (Writer writer = file.openWriter()) {
                            writer.write("%s");
                        } catch (IOException e) {
                            // written for another class of the compilation
                        }
                    });
                }

                @Override
                public VisitorKind getVisitorKind() {
                    return VisitorKind.ISOLATING;
                }
            }
            """.formatted(text));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        int result = compiler.run(null, null, null, "-proc:none", "-d", classes.toString(), source.toString());
        assertEquals(0, result);
        Path services = Files.createDirectories(classes.resolve("META-INF/services"));
        Files.writeString(services.resolve("io.micronaut.inject.visitor.TypeElementVisitor"), "retention.RetentionVisitor\n");
    }

    private record Edit(String name, Change change) {
    }

    @FunctionalInterface
    private interface Change {
        void run() throws IOException;
    }

    private record Project(Path python, Path java, Path output, Path cache) {

        void write(String source) throws IOException {
            Files.writeString(python.resolve("example.py"), source);
        }

        void compile(PythonProcessingSession session, List<File> classpath, List<File> processorPath, List<ClassLoader> classLoaders) {
            PyronautCompiler.Builder builder = PyronautCompiler.builder()
                .pythonSrc(python.toString())
                .javaSrc(java.toString())
                .targetDir(output.toFile())
                .incremental(true)
                .incrementalCacheDirectory(cache.toFile())
                .classpath(classpath)
                .pythonProcessingSession(session)
                .classElementCallback(type -> classLoaders.add(Thread.currentThread().getContextClassLoader()));
            if (processorPath != null) {
                builder.annotationProcessorPath(processorPath);
            }
            builder.build().compile();
        }
    }
}
