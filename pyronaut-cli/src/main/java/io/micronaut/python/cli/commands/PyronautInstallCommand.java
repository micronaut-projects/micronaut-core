/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.python.cli.commands;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.tomlj.Toml;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Command(name = "install", description = "Installs Pyronaut dependencies", mixinStandardHelpOptions = true)
public class PyronautInstallCommand extends AbstractPyronautDependencyResolutionAwareCommand {
    @Option(names = {"--scope"}, required = false)
    String scope;

    @Parameters(index = "0..*", description = "Dependencies to install")
    List<String> extraDependencies = List.of();

    @Override
    public Integer call() {
        var sourceDirectory = resolveSourceDir();
        var tomlFile = sourceDirectory.resolve("pyproject.toml");
        if (Files.exists(tomlFile)) {
            if (!extraDependencies.isEmpty()) {
                try {
                    mutateTomlWithDependencies(tomlFile, extraDependencies, scope);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to update pyproject.toml", e);
                }
            }
            installDependencies(tomlFile);
        } else {
            System.out.println("No pyproject.toml file found.");
        }
        return 0;
    }

    private void installDependencies(Path tomlFile) {
        var outputDir = pyronautVenvCacheDir().resolve("dependencies").toAbsolutePath();
        try (var templateSource = PyronautInstallCommand.class.getResourceAsStream(
            "template.build.gradle")) {
            var pyProject = Toml.parse(tomlFile);
            var repositories = buildRepositoriesBlock(pyProject);
            var scopes = this.scope != null ? List.of(scope) :
                List.copyOf(pyProject.getTableOrEmpty("tool.pyronaut.dependencies").keySet());
            var template = new String(templateSource.readAllBytes(), StandardCharsets.UTF_8)
                .replace("// %REPOSITORIES%", repositories);
            for (var scope : scopes) {
                var destination = outputDir.resolve(scope);
                System.out.println("Resolving " + scope + " dependencies into " + destination);
                var buildScript = template.replace("%DESTINATION_DIR%", destination.toString())
                    .replace("// %DEPENDENCIES%",
                        buildDependenciesList(pyProject, extraDependencies, scope));
                var tmpDir = Files.createTempDirectory("pyronaut");
                Files.write(tmpDir.resolve("settings.gradle"),
                    List.of("rootProject.name = \"pyronaut-resolution\""));
                Files.writeString(tmpDir.resolve("build.gradle"), buildScript);
                try (var connector = GradleConnector.newConnector()
                    .useGradleVersion("9.2.1")
                    .forProjectDirectory(tmpDir.toFile())
                    .connect()) {
                    connector.newBuild()
                        .forTasks("resolvePyronautDependencies")
                        .addProgressListener(PyronautInstallCommand::logEvent,
                            Set.of(OperationType.FILE_DOWNLOAD, OperationType.TASK))
                        .run();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds new dependencies under the specified scope.
     * Supports both TOML formats:
     * 1. [tool.pyronaut] with dependencies.compile = [...]
     * 2. [tool.pyronaut.dependencies] with compile = [...]
     * Preserves formatting and comments outside the target section.
     */
    void mutateTomlWithDependencies(Path tomlFile, List<String> newDeps, String scope) throws IOException {
        // Read original TOML lines
        var lines = Files.readAllLines(tomlFile);
        if (scope == null) {
            scope = "compile"; // Default scope for installation
        }

        List<String> resultLines;
        boolean added;

        // Check if [tool.pyronaut] section exists and contains dotted key dependencies
        var pyronautSection = findTomlSection(lines, "[tool.pyronaut]");
        var hasDottedKeyDeps = false;

        if (pyronautSection.found()) {
            // Check if this section contains dependencies.<scope> = [...]
            for (var i = pyronautSection.contentStartLine; i < pyronautSection.contentEndLine; i++) {
                var line = lines.get(i).trim();
                if (line.startsWith("dependencies." + scope + " = [") ||
                    line.startsWith("dependencies." + scope + "=[")) {
                    hasDottedKeyDeps = true;
                    break;
                }
            }
        }

        if (hasDottedKeyDeps) {
            // Use dotted key format: modify existing dependencies.<scope> in [tool.pyronaut]
            resultLines = modifyDottedKeySection(lines, pyronautSection, newDeps, scope);
            added = !resultLines.equals(lines);
        } else {
            // Use separate section format: [tool.pyronaut.dependencies] with compile = [...]
            var depsSection = findTomlSection(lines, "[tool.pyronaut.dependencies]");
            if (depsSection.found()) {
                // Section exists - modify it while preserving formatting
                resultLines = modifyExistingSection(lines, depsSection, newDeps, scope);
                added = !resultLines.equals(lines);
            } else {
                // Section doesn't exist - add it at the end
                resultLines = addNewSection(lines, "[tool.pyronaut.dependencies]", newDeps, scope);
                added = true;
            }
        }

        if (added) {
            Files.write(tomlFile, resultLines, StandardCharsets.UTF_8);
            System.out.println("Added dependencies to " + tomlFile + " under scope [" + scope + "]");
        } else {
            System.out.println("Dependencies already present in " + tomlFile + " under scope [" + scope + "]");
        }
    }

    /**
     * Finds a TOML section and its boundaries.
     */
    TomlSectionInfo findTomlSection(List<String> lines, String sectionHeader) {
        for (var i = 0; i < lines.size(); i++) {
            var trimmed = lines.get(i).trim();
            if (trimmed.equals(sectionHeader)) {
                var contentStart = i + 1;
                var contentEnd = contentStart;

                // Find the end of this section (next section header or end of file)
                while (contentEnd < lines.size()) {
                    var line = lines.get(contentEnd).trim();
                    if (line.startsWith("[") && line.endsWith("]") && !line.equals(sectionHeader)) {
                        break;
                    }
                    contentEnd++;
                }

                return new TomlSectionInfo(i, contentStart, contentEnd);
            }
        }
        return new TomlSectionInfo();
    }

    /**
     * Modifies a [tool.pyronaut] section with dotted keys like dependencies.compile = [...].
     * Looks for dependencies.<scope> = [...] and modifies it.
     */
    private List<String> modifyDottedKeySection(List<String> lines, TomlSectionInfo sectionInfo,
                                              List<String> newDeps, String scope) {
        List<String> result = new ArrayList<>();

        // Copy everything before the section
        for (var i = 0; i < sectionInfo.headerLine; i++) {
            result.add(lines.get(i));
        }

        // Add the section header
        result.add(lines.get(sectionInfo.headerLine));

        // Find and modify the dotted key array within this section
        var dottedKey = "dependencies." + scope;
        var foundDottedKeyArray = false;
        var i = sectionInfo.contentStartLine;

        while (i < sectionInfo.contentEndLine) {
            var line = lines.get(i);
            var trimmed = line.trim();

            // Look for dependencies.<scope> = [
            if (trimmed.startsWith(dottedKey + " = [") || trimmed.startsWith(dottedKey + "=[")) {
                foundDottedKeyArray = true;
                // Parse the array and add new dependencies
                var modifiedLines = modifyArray(lines, i, sectionInfo.contentEndLine, newDeps);
                result.addAll(modifiedLines);
                // Skip to the end of the array
                i = findArrayEnd(lines, i, sectionInfo.contentEndLine) + 1;
            } else {
                // Copy other lines as-is
                result.add(line);
                i++;
            }
        }

        // If dotted key array not found, add it
        if (!foundDottedKeyArray && !newDeps.isEmpty()) {
            // Add a blank line before the new array if needed
            if (!result.isEmpty() && !result.getLast().trim().isEmpty()) {
                result.add("");
            }
            result.add(dottedKey + " = [");
            for (var j = 0; j < newDeps.size(); j++) {
                var dep = newDeps.get(j);
                result.add("    \"" + dep + "\"" + (j < newDeps.size() - 1 ? "," : ""));
            }
            result.add("]");
        }

        // Copy everything after the section
        for (var j = sectionInfo.contentEndLine; j < lines.size(); j++) {
            result.add(lines.get(j));
        }

        return result;
    }

    /**
     * Modifies an existing [tool.pyronaut.dependencies] section while preserving formatting.
     * Looks for the specified scope array and adds new dependencies to it.
     */
    private List<String> modifyExistingSection(List<String> lines, TomlSectionInfo sectionInfo,
                                             List<String> newDeps, String scope) {
        List<String> result = new ArrayList<>();

        // Copy everything before the section
        for (var i = 0; i < sectionInfo.headerLine; i++) {
            result.add(lines.get(i));
        }

        // Add the section header
        result.add(lines.get(sectionInfo.headerLine));

        // Find and modify the scope array within this section
        var foundScopeArray = false;
        var i = sectionInfo.contentStartLine;

        while (i < sectionInfo.contentEndLine) {
            var line = lines.get(i);
            var trimmed = line.trim();

            // Look for the scope key followed by = [
            if (trimmed.startsWith(scope + " = [") || trimmed.startsWith(scope + "=[")) {
                foundScopeArray = true;
                // Parse the array and add new dependencies
                var modifiedLines = modifyArray(lines, i, sectionInfo.contentEndLine, newDeps);
                result.addAll(modifiedLines);
                // Skip to the end of the array
                i = findArrayEnd(lines, i, sectionInfo.contentEndLine) + 1;
            } else {
                // Copy other lines as-is
                result.add(line);
                i++;
            }
        }

        // If scope array not found, add it
        if (!foundScopeArray && !newDeps.isEmpty()) {
            // Add a blank line before the new array if needed
            if (!result.isEmpty() && !result.getLast().trim().isEmpty()) {
                result.add("");
            }
            result.add(scope + " = [");
            for (var j = 0; j < newDeps.size(); j++) {
                var dep = newDeps.get(j);
                result.add("    \"" + dep + "\"" + (j < newDeps.size() - 1 ? "," : ""));
            }
            result.add("]");
        }

        // Copy everything after the section
        for (var j = sectionInfo.contentEndLine; j < lines.size(); j++) {
            result.add(lines.get(j));
        }

        return result;
    }

    /**
     * Modifies an array starting at the given line, adding new dependencies.
     */
    private List<String> modifyArray(List<String> lines, int arrayStartLine, int sectionEndLine,
                                   List<String> newDeps) {
        List<String> result = new ArrayList<>();
        List<String> existingDeps = new ArrayList<>();

        // Parse existing array content
        var currentLine = arrayStartLine;
        var firstLine = lines.get(currentLine).trim();
        result.add(lines.get(currentLine)); // Add the opening line
        currentLine++;

        // Parse array elements
        while (currentLine < sectionEndLine) {
            var line = lines.get(currentLine);
            var trimmed = line.trim();

            if (trimmed.equals("]")) {
                // End of array - add new dependencies before closing
                for (var newDep : newDeps) {
                    if (!existingDeps.contains(newDep)) {
                        result.add("    \"" + newDep + "\",");
                        existingDeps.add(newDep); // Prevent duplicates
                    }
                }
                result.add(line); // Add the closing bracket
                break;
            } else if (trimmed.startsWith("\"") && trimmed.endsWith("\",")) {
                // Extract dependency from quoted string
                var dep = trimmed.substring(1, trimmed.length() - 2);
                existingDeps.add(dep);
                result.add(line);
            } else if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                // Last element without comma - add comma and then new deps
                var dep = trimmed.substring(1, trimmed.length() - 1);
                existingDeps.add(dep);

                // Add the current line with comma added
                var lineWithComma = line.substring(0, line.lastIndexOf("\"")) + "\",";
                result.add(lineWithComma);

                // Add new dependencies
                for (var newDep : newDeps) {
                    if (!existingDeps.contains(newDep)) {
                        result.add("    \"" + newDep + "\",");
                        existingDeps.add(newDep); // Prevent duplicates
                    }
                }

                // Find and add the closing bracket
                currentLine++;
                while (currentLine < sectionEndLine) {
                    line = lines.get(currentLine);
                    if (lines.get(currentLine).trim().equals("]")) {
                        result.add(line);
                        break;
                    }
                    currentLine++;
                }
                break;
            } else {
                result.add(line); // Comments, empty lines, etc.
            }
            currentLine++;
        }

        return result;
    }

    /**
     * Finds the end of an array starting from the given line.
     */
    private int findArrayEnd(List<String> lines, int startLine, int maxLine) {
        var bracketCount = 0;
        for (var i = startLine; i < maxLine && i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.contains("[")) bracketCount++;
            if (line.contains("]")) {
                bracketCount--;
                if (bracketCount == 0) return i;
            }
        }
        return maxLine;
    }

    /**
     * Adds a new section at the end of the file.
     */
    private List<String> addNewSection(List<String> lines,
                                       String sectionHeader,
                                       List<String> newDeps,
                                       String scope) {
        List<String> result = new ArrayList<>(lines);

        // Add spacing before new section if file doesn't end with empty line
        if (!lines.isEmpty() && !lines.getLast().trim().isEmpty()) {
            result.add("");
        }

        // Add the section header
        result.add(sectionHeader);
        result.add("");

        // Add the scope array
        result.add(scope + " = [");
        for (var j = 0; j < newDeps.size(); j++) {
            var dep = newDeps.get(j);
            result.add("    \"" + dep + "\"" + (j < newDeps.size() - 1 ? "," : ""));
        }
        result.add("]");

        return result;
    }

    /**
     * Extracts dependency name from a dependency line.
     */
    String extractDependencyName(String line) {
        // Handle formats like: name = "version", name="version", name = version, etc.
        var trimmed = line.trim();

        // Skip comments
        if (trimmed.startsWith("#")) {
            return "";
        }

        var equalsIndex = trimmed.indexOf('=');
        if (equalsIndex > 0) {
            return trimmed.substring(0, equalsIndex).trim();
        }
        // Fallback: split on spaces (for cases without =)
        var parts = trimmed.split("\\s+", 2);
        return parts.length > 0 ? parts[0] : "";
    }

    private static void logEvent(ProgressEvent event) {
        System.out.println(event.getDisplayName());
    }

    /**
     * Information about a TOML section's location in the file.
     */
    static class TomlSectionInfo {
        final int headerLine;
        final int contentStartLine;
        final int contentEndLine;
        final boolean found;

        TomlSectionInfo(int headerLine, int contentStartLine, int contentEndLine) {
            this.headerLine = headerLine;
            this.contentStartLine = contentStartLine;
            this.contentEndLine = contentEndLine;
            this.found = true;
        }

        TomlSectionInfo() {
            this.headerLine = -1;
            this.contentStartLine = -1;
            this.contentEndLine = -1;
            this.found = false;
        }

        boolean found() {
            return found;
        }
    }

}
