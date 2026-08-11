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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
