/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PyronautInstallCommandTest {

    @TempDir
    Path tempDir;

    private PyronautInstallCommand createCommand() {
        return new PyronautInstallCommand();
    }

    private Path createTestToml(String content) throws IOException {
        var tomlFile = tempDir.resolve("pyproject.toml");
        Files.writeString(tomlFile, content);
        return tomlFile;
    }

    private String readTomlContent(Path tomlFile) throws IOException {
        return Files.readString(tomlFile);
    }

    @Test
    void testAddDependenciesToExistingSection() throws IOException {
        // Given
        var originalToml = """
            [tool.pyronaut]
            version = "1.0"

            [tool.pyronaut.dependencies]
            compile = [
                "existing-dep"
            ]

            [tool.other]
            value = "test"
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("micronaut-core", "micronaut-http");

        // When
        command.mutateTomlWithDependencies(tomlFile, newDeps, "compile");

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("\"existing-dep\""));
        assertTrue(result.contains("\"micronaut-core\""));
        assertTrue(result.contains("\"micronaut-http\""));
        assertTrue(result.contains("[tool.other]")); // Other sections preserved
        assertTrue(result.contains("value = \"test\""));
    }

    @Test
    void testAddDependenciesToNewSection() throws IOException {
        // Given
        var originalToml = """
            [tool.pyronaut]
            version = "1.0"
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("micronaut-core");

        // When
        command.mutateTomlWithDependencies(tomlFile, newDeps, "compile");

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("[tool.pyronaut.dependencies]"));
        assertTrue(result.contains("compile = ["));
        assertTrue(result.contains("\"micronaut-core\""));
    }

    @Test
    void testPreserveCommentsAndFormatting() throws IOException {
        // Given
        var originalToml = """
            # Project configuration
            [tool.pyronaut]
            version = "1.0"

            # Dependencies section
            [tool.pyronaut.dependencies]
            compile = [
                "existing-dep"
            ]

            # Another section
            [tool.other]
            value = "test"
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("new-dep");

        // When
        command.mutateTomlWithDependencies(tomlFile, newDeps, "compile");

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("# Project configuration"));
        assertTrue(result.contains("# Dependencies section"));
        assertTrue(result.contains("# Another section"));
        assertTrue(result.contains("\"existing-dep\""));
        assertTrue(result.contains("\"new-dep\""));
        assertTrue(result.contains("[tool.other]"));
        assertTrue(result.contains("value = \"test\""));
    }

    @Test
    void testSkipDuplicateDependencies() throws IOException {
        // Given
        var originalToml = """
            [tool.pyronaut.dependencies]
            compile = [
                "existing-dep"
            ]
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("existing-dep", "new-dep");

        // When
        command.mutateTomlWithDependencies(tomlFile, newDeps, "compile");

        // Then
        var result = readTomlContent(tomlFile);
        System.out.println("Result:\n" + result);
        // Should contain existing-dep only once
        assertEquals(1, result.split("\"existing-dep\"", -1).length - 1);
        assertTrue(result.contains("\"new-dep\""));
    }

    @Test
    void testHandleComplexFormatting() throws IOException {
        // Given
        var originalToml = """
            [tool.pyronaut.dependencies]
            # Inline comment
            compile = [
                "dep1",  # another comment
                "dep2"
            ]

            [tool.next]
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("dep3");

        // When
        command.mutateTomlWithDependencies(tomlFile, newDeps, "compile");

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("# Inline comment"));
        assertTrue(result.contains("\"dep1\",  # another comment"));
        assertTrue(result.contains("\"dep2\""));
        assertTrue(result.contains("\"dep3\""));
        assertTrue(result.contains("[tool.next]"));
    }

    @Test
    void testMultipleScopes() throws IOException {
        // Given
        var originalToml = """
            [tool.pyronaut.dependencies]
            compile = [
                "compile-dep"
            ]
            test = [
                "test-dep"
            ]
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();

        // When - add to test scope
        command.mutateTomlWithDependencies(tomlFile, List.of("new-test-dep"), "test");

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("\"compile-dep\""));
        assertTrue(result.contains("\"test-dep\""));
        assertTrue(result.contains("\"new-test-dep\""));
    }

    @Test
    void testDefaultScopeHandling() throws IOException {
        // Given
        var originalToml = """
            [tool.pyronaut]
            version = "1.0"
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("micronaut-core");

        // When - no scope specified (should default to "compile")
        command.mutateTomlWithDependencies(tomlFile, newDeps, null);

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("[tool.pyronaut.dependencies]"));
        assertTrue(result.contains("compile = ["));
        assertTrue(result.contains("\"micronaut-core\""));
    }

    @Test
    void testDependencyNameExtraction() {
        var command = createCommand();

        // Test various formats
        assertEquals("dep1", command.extractDependencyName("dep1 = \"1.0\""));
        assertEquals("dep2", command.extractDependencyName("dep2=\"2.0\""));
        assertEquals("dep3", command.extractDependencyName("dep3"));
        assertEquals("complex-dep", command.extractDependencyName("complex-dep = \"*\"  # comment"));
        assertEquals("", command.extractDependencyName(""));
        assertEquals("", command.extractDependencyName("# comment"));
    }

    @Test
    void testDottedKeyFormat() throws IOException {
        // Given - user's example format with dotted keys
        var originalToml = """
            [project]
            name="pyronaut-demo"

            [tool.pyronaut]
            version="5.0.0-SNAPSHOT"
            repositories = [
                "mavenCentral",
                "mavenLocal",
                "https://repo.gradle.org/gradle/libs-releases"
            ]

            dependencies.compile = [
                "io.micronaut:micronaut-inject-python",
                "io.micronaut:micronaut-context-python"
            ]

            annotationProcessor = [
                "io.micronaut:micronaut-inject-python",
                "io.micronaut:micronaut-context-python",
            ]
            """;

        var tomlFile = createTestToml(originalToml);
        var command = createCommand();
        var newDeps = List.of("com.foo:bar");

        // When
        command.mutateTomlWithDependencies(tomlFile, newDeps, "compile");

        // Then
        var result = readTomlContent(tomlFile);
        assertTrue(result.contains("dependencies.compile = ["));
        assertTrue(result.contains("\"io.micronaut:micronaut-inject-python\","));
        assertTrue(result.contains("\"io.micronaut:micronaut-context-python\","));
        assertTrue(result.contains("\"com.foo:bar\""));
        // Should not have created a separate [tool.pyronaut.dependencies] section
        assertFalse(result.contains("[tool.pyronaut.dependencies]"));
    }

    @Test
    void testSectionBoundaryDetection() {
        var command = createCommand();

        var lines = List.of(
            "[tool.pyronaut]",
            "version = \"1.0\"",
            "",
            "[tool.pyronaut.dependencies.default]",
            "dep1 = \"*\"",
            "dep2 = \"*\"",
            "",
            "[tool.other]",
            "value = \"test\""
        );

        var sectionInfo = command.findTomlSection(lines, "[tool.pyronaut.dependencies.default]");

        assertTrue(sectionInfo.found());
        assertEquals(3, sectionInfo.headerLine); // Index of section header
        assertEquals(4, sectionInfo.contentStartLine); // After header
        assertEquals(7, sectionInfo.contentEndLine); // Before next section
    }
}
