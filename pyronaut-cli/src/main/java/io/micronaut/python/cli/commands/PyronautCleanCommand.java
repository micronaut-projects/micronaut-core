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
import picocli.CommandLine;

import java.nio.file.Files;
import java.util.List;

@CommandLine.Command(name = "clean", description = "Deletes the temporary files", mixinStandardHelpOptions = true)
public class PyronautCleanCommand extends BaseSourceCommand {
    private static final List<String> EXTRA_DIRS_TO_DELETE = List.of("build", "dist");

    @Override
    public Integer call() throws Exception {
        var rootDir = resolveRootDir();
        FileUtils.recurseDelete(
            FileUtils.resolveOutputDirectory(rootDir)
        );
        for (var dirName : EXTRA_DIRS_TO_DELETE) {
            var dir = rootDir.resolve(dirName);
            if (Files.isDirectory(dir)) {
                FileUtils.recurseDelete(dir);
            }
        }
        return 0;
    }
}
