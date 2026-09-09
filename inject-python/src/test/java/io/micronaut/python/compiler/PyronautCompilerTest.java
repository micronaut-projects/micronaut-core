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

import io.micronaut.context.python.annotation.PythonApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PyronautCompilerTest {

    @Test
    void preservesOriginalInlineSourceInTheInMemoryVfs() throws Exception {
        String source = "# debugger comment\r\n\r\nanswer  =  42  # spacing\r\n";
        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonCode(source)
            .build()
            .buildClassLoader();

        try (var input = classLoader.getResourceAsStream(
            "META-INF/GRAALPY-VFS/micronaut-application/src/__main__.py"
        )) {
            assertNotNull(input);
            assertEquals(source, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void compilesJavaSourcesWithoutPythonApplication(@TempDir Path sourceDirectory) throws Exception {
        Files.writeString(sourceDirectory.resolve("Greeting.java"), "public class Greeting {}\n");

        ClassLoader classLoader = PyronautCompiler.builder()
            .javaSrc(sourceDirectory.toString())
            .build()
            .buildClassLoader();

        Class<?> mainClass = classLoader.loadClass("pyronaut_application.PyronautMain");
        assertNotNull(mainClass.getMethod("main", String[].class));
        assertFalse(mainClass.isAnnotationPresent(PythonApplication.class));
        assertNotNull(classLoader.loadClass("Greeting"));
    }

    @Test
    void loadsApplicationDataProcessorsBeforeLauncherProcessors(@TempDir Path directory) throws Exception {
        Path sourceDirectory = Files.createDirectories(directory.resolve("source/io/micronaut/data/processor"));
        Path source = sourceDirectory.resolve("ProcessorClassLoaderMarker.java");
        Files.writeString(source, """
            package io.micronaut.data.processor;

            public final class ProcessorClassLoaderMarker {
                private ProcessorClassLoaderMarker() {}
                public static String origin() { return "application"; }
            }
            """);
        Path classes = Files.createDirectories(directory.resolve("classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
            null,
            null,
            null,
            "-d",
            classes.toString(),
            source.toString()
        ));

        Path launcherSourceDirectory = Files.createDirectories(directory.resolve("launcher-source/io/micronaut/data/processor"));
        Path launcherSource = launcherSourceDirectory.resolve("ProcessorClassLoaderMarker.java");
        Files.writeString(launcherSource, """
            package io.micronaut.data.processor;

            public final class ProcessorClassLoaderMarker {
                private ProcessorClassLoaderMarker() {}
                public static String origin() { return "launcher"; }
            }
            """);
        Path launcherClasses = Files.createDirectories(directory.resolve("launcher-classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(
            null,
            null,
            null,
            "-d",
            launcherClasses.toString(),
            launcherSource.toString()
        ));

        Path launcherJar = directory.resolve("launcher-processors.jar");
        try (OutputStream output = Files.newOutputStream(launcherJar);
             JarOutputStream jar = new JarOutputStream(output);
             var paths = Files.walk(launcherClasses)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String entryName = launcherClasses.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                jar.putNextEntry(new JarEntry(entryName));
                Files.copy(path, jar);
                jar.closeEntry();
            }
        }

        Path processorJar = directory.resolve("application-processors.jar");
        try (OutputStream output = Files.newOutputStream(processorJar);
             JarOutputStream jar = new JarOutputStream(output)) {
            try (var paths = Files.walk(classes)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String entryName = classes.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                    jar.putNextEntry(new JarEntry(entryName));
                    Files.copy(path, jar);
                    jar.closeEntry();
                }
            }
        }

        try (URLClassLoader launcherClassLoader = new URLClassLoader(
            new URL[] {launcherJar.toUri().toURL()},
            PyronautCompilerTest.class.getClassLoader());
             URLClassLoader classLoader = (URLClassLoader) PyronautJavaCompiler
                 .createAnnotationProcessorClassLoader(List.of(processorJar.toFile()), launcherClassLoader)) {
            // Parent-first loading is the pre-fix behavior: the launcher's copy wins
            // when both classpaths contain the same processor FQCN.
            Class<?> parentMarker = Class.forName(
                "io.micronaut.data.processor.ProcessorClassLoaderMarker",
                true,
                launcherClassLoader
            );
            assertEquals("launcher", parentMarker.getMethod("origin").invoke(null));

            Class<?> marker = Class.forName(
                "io.micronaut.data.processor.ProcessorClassLoaderMarker",
                true,
                classLoader
            );
            assertEquals("application", marker.getMethod("origin").invoke(null));
        }
    }

    @Test
    void compilesPythonMethodsReturningHttpResponseSubtypes(@TempDir Path sourceDirectory) throws Exception {
        Files.writeString(sourceDirectory.resolve("responses.py"), """
            from micronaut.http import MutableHttpResponse

            class Responses:
                def response(self) -> MutableHttpResponse:
                    return None
            """);

        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .build()
            .buildClassLoader();

        assertNotNull(classLoader.loadClass("pyronaut_application.PyronautMain"));
    }

    @Test
    void compilesPythonListSubclass(@TempDir Path sourceDirectory) throws Exception {
        Files.writeString(sourceDirectory.resolve("candidates.py"), """
            class Candidates(list):
                def __init__(self):
                    super().__init__()
            """);

        Path outputDirectory = Files.createDirectories(sourceDirectory.resolve("output"));
        PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .targetDir(outputDirectory.toFile())
            .build()
            .compile();

        String generated = Files.readString(findGeneratedSource(outputDirectory, "Candidates.java"));
        assertTrue(generated.contains("Object[] toArray()"));
        assertTrue(generated.contains("<T> T[] toArray(T[]"));
    }

    @Test
    void generatedJavaInterfaceBridgeInvokesPythonMethod(@TempDir Path directory) throws Exception {
        Path javaSource = Files.createDirectories(directory.resolve("java").resolve("callback"));
        Files.writeString(javaSource.resolve("Callback.java"), """
            package callback;

            public interface Callback {
                String invoke(String value);
            }
            """);
        Path pythonSource = Files.createDirectories(directory.resolve("python"));
        Files.writeString(pythonSource.resolve("implementation.py"), """
            from callback import Callback

            class Implementation(Callback):
                def invoke(self, value: str) -> str:
                    return value
            """);
        Path output = Files.createDirectories(directory.resolve("output"));

        PyronautCompiler.builder()
            .javaSrc(directory.resolve("java").toString())
            .pythonSrc(pythonSource.toString())
            .targetDir(output.toFile())
            .build()
            .compile();

        String generated = Files.readString(findGeneratedSource(output, "Implementation.java"));
        assertEquals(1, generated.split("PythonInvocation.invokePythonMethod", -1).length - 1);
    }

    @Test
    void generatedBridgePreservesDefaultAsyncInterfaceMethod(@TempDir Path directory) throws Exception {
        Path javaSource = Files.createDirectories(directory.resolve("java").resolve("callback"));
        Files.writeString(javaSource.resolve("AsyncSender.java"), """
            package callback;

            import io.micronaut.core.naming.Named;
            import java.util.function.Consumer;

            public interface AsyncSender extends Named {
                default String sendAsync(String email) {
                    return sendAsync(email, ignored -> { });
                }

                String sendAsync(String email, Consumer<String> emailRequest);
            }
            """);
        Path pythonSource = Files.createDirectories(directory.resolve("python"));
        Files.writeString(pythonSource.resolve("sender.py"), """
            from callback import AsyncSender

            class Sender(AsyncSender):
                def getName(self) -> str:
                    return "sender"

                def sendAsync(self, email: str, email_request=None) -> str:
                    return email
            """);
        Path output = Files.createDirectories(directory.resolve("output"));

        PyronautCompiler.builder()
            .javaSrc(directory.resolve("java").toString())
            .pythonSrc(pythonSource.toString())
            .targetDir(output.toFile())
            .build()
            .compile();

        String generated = Files.readString(findGeneratedSource(output, "Sender.java"));
        assertEquals(0, generated.split("sendAsync\\(String email\\)", -1).length - 1);
        assertEquals(1, generated.split("sendAsync\\(String email,", -1).length - 1);
    }

    @Test
    void compilesParameterizedIntrospectedPythonConfigurationBean(@TempDir Path sourceDirectory) throws Exception {
        Files.writeString(sourceDirectory.resolve("team_configuration.py"), """
            from dataclasses import dataclass, field
            from typing import Annotated

            from micronaut.context.annotation import ConfigurationBuilder, ConfigurationProperties
            from micronaut.core.annotation import Introspected

            @Introspected
            class TeamBuilder:
                value: str | None = None

                def with_name(self, value: str):
                    self.value = value
                    return self

            @Introspected
            @dataclass(frozen=True)
            class TeamId:
                value: str

            @ConfigurationProperties("team")
            @Introspected
            @dataclass(init=False)
            class TeamConfiguration:
                name: str | None = None
                player_names: list[str] = field(default_factory=list)
                builder: Annotated[TeamBuilder, ConfigurationBuilder(prefixes="with_", configurationPrefix="team-admin")] = field(init=False)

                def __init__(self, name: str | None = None, player_names: list[str] | None = None):
                    self.name = name
                    self.player_names = player_names or []
                    self.builder = TeamBuilder()
        """.indent(-4));

        Path outputDirectory = sourceDirectory.resolve("output");
        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .build()
            .buildClassLoader();

        // Compilation must complete for the parameterized configuration shape;
        // the generated application entry point is the stable in-memory output.
        assertNotNull(classLoader.loadClass("pyronaut_application.PyronautMain"));

        Files.createDirectories(outputDirectory);
        PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .targetDir(outputDirectory.toFile())
            .build()
            .compile();
        String generated = Files.readString(findGeneratedSource(outputDirectory, "TeamConfiguration.java"));
        assertTrue(generated.contains("PythonContextRuntime.newInstance"));
        assertTrue(generated.contains("PooledValueCoercible"));
        assertTrue(generated.contains("asPolyglotValue(Context "));
        assertTrue(generated.contains("reconstructPolyglotValue(Context "));
        assertTrue(generated.contains("newUninitializedInstance(arg1,"));
        assertTrue(generated.contains("PythonCoercion.coerceToContext"));

        String frozen = Files.readString(findGeneratedSource(outputDirectory, "TeamId.java"));
        assertTrue(frozen.contains("PooledValueCoercible"));
        assertTrue(frozen.contains("reconstructPolyglotValue(Context "));
        assertTrue(frozen.contains("PythonContextRuntime.setInstanceProperty"));

        String regularClass = Files.readString(findGeneratedSource(outputDirectory, "TeamBuilder.java"));
        assertTrue(regularClass.contains("PooledValueCoercible"));
        assertTrue(regularClass.contains("asPolyglotValue(Context "));
        assertTrue(regularClass.contains("newUninitializedInstance(arg1,"));

    }

    @Test
    void optionallyIncludesPythonBytecodeInTheInMemoryVfs() throws Exception {
        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonCode("answer = 42")
            .compilePythonBytecode(true)
            .build()
            .buildClassLoader();

        String filesList;
        try (var input = classLoader.getResourceAsStream("META-INF/GRAALPY-VFS/micronaut-application/fileslist.txt")) {
            assertNotNull(input);
            filesList = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(filesList.contains("__main__.py"));
        assertTrue(filesList.contains("__pycache__/__main__."));
        assertTrue(filesList.contains(".pyc"));
    }

    @Test
    void leavesPythonBytecodeOutOfTheInMemoryVfsByDefault() throws Exception {
        ClassLoader classLoader = PyronautCompiler.builder()
            .pythonCode("answer = 42")
            .build()
            .buildClassLoader();

        String filesList;
        try (var input = classLoader.getResourceAsStream("META-INF/GRAALPY-VFS/micronaut-application/fileslist.txt")) {
            assertNotNull(input);
            filesList = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(filesList.contains("__main__.py"));
        assertFalse(filesList.contains(".pyc"));
    }

    private static Path findGeneratedSource(Path outputDirectory, String fileName) throws Exception {
        try (var paths = Files.walk(outputDirectory)) {
            return paths
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow();
        }
    }
}
