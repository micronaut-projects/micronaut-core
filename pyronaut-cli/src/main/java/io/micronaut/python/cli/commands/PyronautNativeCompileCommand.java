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

import io.micronaut.python.cli.util.FileUtils;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.events.ProgressEvent;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Command(name = "native", description = "Builds a native image of the Pyronaut application", mixinStandardHelpOptions = true)
public class PyronautNativeCompileCommand extends AbstractPyronautDependencyResolutionAwareCommand {

    @Override
    public Integer call() {
        var sourceDirectory = resolveRootDir();
        var tomlFile = sourceDirectory.resolve("pyproject.toml");
        if (Files.exists(tomlFile)) {
            buildNativeImage(tomlFile);
        } else {
            System.out.println("No pyproject.toml file found.");
        }
        return 0;
    }

    private void buildNativeImage(Path tomlFile) {
        try (var templateSource = PyronautNativeCompileCommand.class.getResourceAsStream(
            "native.build.gradle")) {
            var pyProject = Toml.parse(tomlFile);
            var repositories = buildRepositoriesBlock(pyProject);
            var template = new String(templateSource.readAllBytes(), StandardCharsets.UTF_8)
                .replace("// %REPOSITORIES%", repositories);
                var imageName = findImageName(pyProject);
            var rootDirectory = tomlFile.getParent();
            var pyronautDir = rootDirectory.resolve(FileUtils.PYRONAUT_DIR);
            var classesDir = pyronautDir.resolve(FileUtils.CLASSES_DIR);
            var buildScript = template.replace("// %DEPENDENCIES%", buildDependenciesList(pyProject, List.of(), "compile"))
                    .replace("%MAIN_CLASS%", "pyronaut_application.PyronautMain")
                    .replace("%PYRONAUT_PATH%", classesDir.toAbsolutePath().toString())
                    .replace("%PYRONAUT_CONFIG%", rootDirectory.resolve("config").toAbsolutePath().toString())
                    .replace("%IMAGE_NAME%", imageName)
                    .replace("%DESTINATION_DIR%", pyronautDir.resolve(FileUtils.NATIVE_EXPORT_DIR).toAbsolutePath().toString());
                withTemporaryDir(tmpDir -> {
                    Files.write(tmpDir.resolve("settings.gradle"),
                        List.of("rootProject.name = \"pyronaut-compile\""));
                    Files.writeString(tmpDir.resolve("build.gradle"), buildScript);
                    try (var connector = GradleConnector.newConnector()
                        .useGradleVersion("9.2.1")
                        .forProjectDirectory(tmpDir.toFile())
                        .connect()) {
                        connector.newBuild()
                            .forTasks("exportBinary")
                            .setStandardError(System.err)
                            .setStandardOutput(System.out)
                            .run();
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String findImageName(TomlParseResult pyProject) {
        var projectName = pyProject.getString("project.name");
        if (projectName != null) {
            return projectName;
        }
        return "pyronaut-app";
    }

    private static void logEvent(ProgressEvent event) {
        System.out.println(event.getDisplayName());
    }

}
