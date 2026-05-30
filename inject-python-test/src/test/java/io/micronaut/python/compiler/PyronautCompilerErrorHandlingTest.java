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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        try (var paths = Files.list(dumpDirectory.toPath())) {
            return paths.count();
        }
    }
}
