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
import org.tomlj.TomlParseResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "install", description = "Installs Pyronaut dependencies", mixinStandardHelpOptions = true)
public class PyronautInstallCommand extends BaseSourceCommand {
    @Option(names = {"--scope"}, required = false)
    String scope;

    @Parameters(index = "0..*", description = "Dependencies to install")
    List<String> extraDependencies = List.of();

    @Override
    public Integer call() {
        var sourceDirectory = resolveSourceDir();
        var tomlFile = sourceDirectory.resolve("pyproject.toml");
        if (Files.exists(tomlFile)) {
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

    private static void logEvent(ProgressEvent event) {
        System.out.println(event.getDisplayName());
    }

    private String buildRepositoriesBlock(TomlParseResult pyProject) {
        var sb = new StringBuilder();
        var repos = pyProject.getArray("tool.pyronaut.repositories");
        if (repos == null || repos.isEmpty()) {
            sb.append("    mavenCentral()\n");
        } else {
            for (var repo : repos.toList()) {
                if (repo instanceof String repoName) {
                    if ("mavenCentral".equals(repoName)) {
                        sb.append("    mavenCentral()\n");
                    } else if ("mavenLocal".equals(repoName)) {
                        sb.append("    mavenLocal()\n");
                    } else {
                        sb.append("    maven { url =\"").append(repoName).append("\" }\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    private String buildDependenciesList(TomlParseResult pyProject, List<String> extraDependencies,
                                         String scope) throws IOException {
        var depsArray = pyProject.getArray("tool.pyronaut.dependencies." + scope);
        if (depsArray == null && extraDependencies.isEmpty()) {
            return "";
        }
        var bomVersion = pyProject.getString("tool.pyronaut.version");
        String platform = null;
        if (bomVersion != null) {
            // TODO: Should be replaced with platform BOM, not core BOM, when we have a milestone
            platform =
                "    implementation(platform(\"io.micronaut:micronaut-core-bom:" + bomVersion + "\"))\n"+
                "    implementation(platform(\"io.micronaut.platform:micronaut-platform:4.10.2\"))\n";
        }
        var allDependencies = depsArray == null ? extraDependencies.stream() :
            Stream.concat(depsArray.toList().stream(), extraDependencies.stream());
        var deps = allDependencies
            .map(d -> "    implementation(\"" + d + "\")")
            .collect(Collectors.joining("\n"));
        if (platform != null) {
            return platform + "\n" + deps;
        }
        return deps;
    }

}
