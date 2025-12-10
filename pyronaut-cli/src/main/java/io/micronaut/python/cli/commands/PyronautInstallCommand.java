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

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.tomlj.Toml;
import com.github.marschall.toml.TomlBuilder;
import com.github.marschall.toml.TomlTableBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Adds new dependencies under [tool.pyronaut.dependencies.<scope>] in the TOML.
     */
    private void mutateTomlWithDependencies(Path tomlFile, List<String> newDeps, String scope) throws IOException {
        // Read original TOML lines
        List<String> lines = Files.readAllLines(tomlFile);
        String sectionHeader;
        if (scope == null) {
            // fallback to "default" if not specified
            scope = "default";
        }
        sectionHeader = "[tool.pyronaut.dependencies." + scope + "]";
        boolean foundSection = false, added = false;
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i).trim();
            if (line.equals(sectionHeader)) {
                foundSection = true;
                // Insert after the section header, skip any existing deps with same name
                i++;
                for (String dep : newDeps) {
                    String depKey = dep.split("[ =]", 2)[0].trim();
                    boolean alreadyDeclared = false;
                    int j = i;
                    while (j < lines.size() && !lines.get(j).startsWith("[")) {
                        if (lines.get(j).trim().startsWith(depKey + " ")) {
                            alreadyDeclared = true;
                            break;
                        }
                        j++;
                    }
                    if (!alreadyDeclared) {
                        lines.add(i, dep + " = \"*\""); // naive, could parse versions
                        i++;
                        added = true;
                    }
                }
                break;
            }
            i++;
        }
        if (!foundSection) {
            // add section at end
            lines.add("");
            lines.add(sectionHeader);
            for (String dep : newDeps) {
                lines.add(dep + " = \"*\"");
            }
            added = true;
        }
        if (added) {
            Files.write(tomlFile, lines, StandardCharsets.UTF_8);
            System.out.println("Added dependencies to " + tomlFile + " under scope ["+scope+"]");
        } else {
            System.out.println("Dependencies already present in " + tomlFile + " under scope ["+scope+"]");
        }
    }

    private static void logEvent(ProgressEvent event) {
        System.out.println(event.getDisplayName());
    }

}
