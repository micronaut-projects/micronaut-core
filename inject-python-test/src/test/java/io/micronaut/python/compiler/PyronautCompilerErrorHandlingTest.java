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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PyronautCompilerErrorHandlingTest {

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetFailingVisitor() {
        FailingTypeElementVisitor.phase = null;
    }

    @Test
    void buildClassLoaderReportsConciseInlinePythonSyntaxError() throws IOException {
        File dumpDirectory = temporaryDirectory.resolve("dumps").toFile();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode("class Broken(")
            .errorDumpDirectory(dumpDirectory)
            .build()
            .buildClassLoader());

        assertConciseSyntaxError(exception.getMessage(), "class Broken(", dumpDirectory);
        assertEquals(1, countDumpFiles(dumpDirectory));
    }

    @Test
    void compileReportsConciseFilePythonSyntaxError() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("src");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Broken.py"), "class Broken(");
        File dumpDirectory = temporaryDirectory.resolve("dumps").toFile();
        File targetDirectory = temporaryDirectory.resolve("classes").toFile();
        Files.createDirectories(targetDirectory.toPath());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .targetDir(targetDirectory)
            .errorDumpDirectory(dumpDirectory)
            .build()
            .compile());

        String message = exception.getMessage();
        assertConciseSyntaxError(message, "class Broken(", dumpDirectory);
        assertTrue(message.contains("Broken.py"));
        assertEquals(1, countDumpFiles(dumpDirectory));
    }

    @Test
    void compileReportsConciseCustomInitPyError() throws IOException {
        Path sourceDirectory = temporaryDirectory.resolve("src");
        Path packageDirectory = sourceDirectory.resolve("simple_python");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("__init__.py"), "");
        Files.writeString(packageDirectory.resolve("Controller.py"), "class Controller: pass");
        File dumpDirectory = temporaryDirectory.resolve("dumps").toFile();
        File targetDirectory = temporaryDirectory.resolve("classes").toFile();
        Files.createDirectories(targetDirectory.toPath());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonSrc(sourceDirectory.toString())
            .targetDir(targetDirectory)
            .errorDumpDirectory(dumpDirectory)
            .build()
            .compile());

        String message = exception.getMessage();
        assertTrue(message.contains("Custom __init__.py files are not supported in Pyronaut applications"), message);
        assertTrue(message.contains("simple_python/__init__.py"), message);
        assertFalse(message.contains("Output stream or writer has already been opened"), message);
        assertEquals(1, countDumpFiles(dumpDirectory));
    }

    @Test
    void verboseErrorsIncludeFullDiagnosticsAndStillWriteDump() throws IOException {
        File dumpDirectory = temporaryDirectory.resolve("dumps").toFile();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode("class Broken(")
            .errorDumpDirectory(dumpDirectory)
            .verboseErrors(true)
            .build()
            .buildClassLoader());

        String message = exception.getMessage();
        assertTrue(message.contains("Pyronaut processing failed with verbose diagnostics"));
        assertTrue(message.contains("Diagnostics:"));
        assertTrue(message.contains("SyntaxError"));
        assertTrue(message.contains(dumpDirectory.getAbsolutePath()));
        assertEquals(1, countDumpFiles(dumpDirectory));
    }

    @Test
    void buildClassLoaderReportsConciseTypeVisitorRuntimeException() throws IOException {
        File dumpDirectory = temporaryDirectory.resolve("dumps").toFile();
        FailingTypeElementVisitor.phase = FailingTypeElementVisitor.Phase.VISIT_CLASS;

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .errorDumpDirectory(dumpDirectory)
            .build()
            .buildClassLoader());

        assertConciseVisitorError(exception.getMessage(), "visitClass", "visit exploded", dumpDirectory);
        assertEquals(1, countDumpFiles(dumpDirectory));
    }

    @Test
    void compileReportsConciseTypeVisitorRuntimeException() throws IOException {
        File dumpDirectory = temporaryDirectory.resolve("dumps").toFile();
        File targetDirectory = temporaryDirectory.resolve("classes").toFile();
        Files.createDirectories(targetDirectory.toPath());
        FailingTypeElementVisitor.phase = FailingTypeElementVisitor.Phase.FINISH;

        RuntimeException exception = assertThrows(RuntimeException.class, () -> PyronautCompiler.builder()
            .pythonCode("class Test: pass")
            .targetDir(targetDirectory)
            .errorDumpDirectory(dumpDirectory)
            .build()
            .compile());

        assertConciseVisitorError(exception.getMessage(), "finish", "finish exploded", dumpDirectory);
        assertEquals(1, countDumpFiles(dumpDirectory));
    }

    @Test
    void compileWritesDumpNextToTargetDirectoryByDefault() throws IOException {
        Path buildDirectory = temporaryDirectory.resolve("build");
        File targetDirectory = buildDirectory.resolve("classes").toFile();
        Files.createDirectories(targetDirectory.toPath());
        File expectedDumpDirectory = buildDirectory.resolve("processor-error-dumps").toFile();

        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode("class Broken(")
            .targetDir(targetDirectory)
            .build();

        RuntimeException exception = assertThrows(RuntimeException.class, compiler::compile);

        assertConciseSyntaxError(exception.getMessage(), "class Broken(", expectedDumpDirectory);
        assertEquals(1, countDumpFiles(expectedDumpDirectory));
        assertFalse(exception.getMessage().contains(System.getProperty("user.home") + File.separator + ".pyronaut"), exception.getMessage());
        assertEquals(0, countDumpFiles(targetDirectory.toPath().resolve("processor-error-dumps")));
    }

    @Test
    void buildClassLoaderWritesDumpToPrivateTemporaryDirectoryByDefault() throws IOException {
        PyronautCompiler compiler = PyronautCompiler.builder()
            .pythonCode("class Broken(")
            .build();

        RuntimeException exception = assertThrows(RuntimeException.class, compiler::buildClassLoader);

        String message = exception.getMessage();
        String marker = "Full error details were written to: ";
        int start = message.indexOf(marker);
        assertTrue(start >= 0, message);
        Path dumpFile = Path.of(message.substring(start + marker.length()).strip());
        try {
            assertTrue(Files.isRegularFile(dumpFile), message);
            assertTrue(dumpFile.getParent().getFileName().toString().startsWith("pyronaut-processor-error-dumps-"), message);
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dumpFile.getParent())));
            }
            assertFalse(dumpFile.startsWith(Path.of(System.getProperty("user.home"), ".pyronaut")), message);
        } finally {
            Files.deleteIfExists(dumpFile);
            Files.deleteIfExists(dumpFile.getParent());
        }
    }

    @Test
    void defaultErrorDumpDirectoryIsNextToTargetDirectory() {
        File relativeTarget = new File("build/classes");
        assertEquals(
            new File(relativeTarget.getAbsoluteFile().getParentFile(), "processor-error-dumps"),
            PyronautJavaCompiler.defaultErrorDumpDirectory(relativeTarget)
        );
    }

    @Test
    void javaCompilerHasNoStaticFieldDerivedFromUserHome() throws IllegalAccessException {
        // io.micronaut.python.compiler is initialised at build time in native images, so a static
        // path derived from the build host's environment would be baked into the released binary.
        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");
        assertNotNull(userHome);
        assertNotNull(userDir);
        for (Field field : PyronautJavaCompiler.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof File || value instanceof Path || value instanceof CharSequence) {
                String text = value.toString();
                assertFalse(text.contains(userHome), "static field " + field.getName() + " is derived from user.home: " + text);
                assertFalse(text.contains(userDir), "static field " + field.getName() + " is derived from user.dir: " + text);
            }
        }
    }

    private static void assertConciseSyntaxError(String message, String snippet, File dumpDirectory) {
        assertTrue(message.contains("Pyronaut processing failed"), message);
        assertTrue(message.contains("SyntaxError"), message);
        assertTrue(message.contains("Location:"), message);
        assertTrue(message.contains("Python snippet:"), message);
        assertTrue(message.contains(snippet), message);
        assertTrue(message.contains(dumpDirectory.getAbsolutePath()), message);
        assertFalse(message.contains("Failed Trace:"));
        assertFalse(message.contains("org.graalvm.polyglot"));
        assertFalse(message.contains("\tat "));
    }

    private static void assertConciseVisitorError(String message, String phase, String primaryMessage, File dumpDirectory) {
        assertTrue(message.contains("Pyronaut processing failed"), message);
        assertTrue(message.contains(FailingTypeElementVisitor.class.getName()), message);
        assertTrue(message.contains(phase), message);
        assertTrue(message.contains(primaryMessage), message);
        assertTrue(message.contains(dumpDirectory.getAbsolutePath()), message);
        assertFalse(message.contains("\tat "));
    }

    private static long countDumpFiles(File dumpDirectory) throws IOException {
        return countDumpFiles(dumpDirectory.toPath());
    }

    private static long countDumpFiles(Path dumpDirectory) throws IOException {
        if (!Files.isDirectory(dumpDirectory)) {
            return 0;
        }
        try (var paths = Files.list(dumpDirectory)) {
            return paths.count();
        }
    }
}
