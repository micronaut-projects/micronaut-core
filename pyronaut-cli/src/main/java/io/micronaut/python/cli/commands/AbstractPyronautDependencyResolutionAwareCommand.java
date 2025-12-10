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

import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractPyronautDependencyResolutionAwareCommand extends BaseSourceCommand {
    protected String buildRepositoriesBlock(TomlParseResult pyProject) {
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

    protected String buildDependenciesList(TomlParseResult pyProject,
                                           List<String> extraDependencies,
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
                "    implementation(platform(\"io.micronaut:micronaut-core-bom:" + bomVersion + "\"))\n" +
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
