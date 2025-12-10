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
package io.micronaut.python.cli.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An in-memory representation of the content
 * of a Maven repository on disk. The expected
 * layout is groupid/artifactid/version/[files]
 */
public class PythonMavenRepository {
    private final Path repositoryPath;
    private final List<MavenArtifact> artifacts;
    private final Map<String, Map<String, List<MavenArtifact>>> index;

    private PythonMavenRepository(Path repositoryPath, List<MavenArtifact> artifacts) {
        this.repositoryPath = repositoryPath;
        this.artifacts = artifacts;
        this.index = artifacts.stream()
            .collect(Collectors.groupingBy(
                MavenArtifact::groupId,
                Collectors.groupingBy(MavenArtifact::artifactId)
            ));
    }

    public List<File> visitRepo(Predicate<? super MavenArtifact> predicate) {
        return artifacts.stream()
            .filter(predicate)
            .map(artifact -> {
                var dir = repositoryPath.resolve(artifact.groupId() + "/" + artifact.artifactId() + "/" + artifact.version()).toFile();
                var jars = dir.listFiles(f -> f.getName().endsWith(".jar"));
                if (jars != null && jars.length>0) {
                    return jars[0];
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * The path to the repository
     * @return the path to the repository
     */
    public Path getRepositoryPath() {
        return repositoryPath;
    }

    /**
     * The list of artifacts in that repository
     * @return the artifact list
     */
    public List<MavenArtifact> getArtifacts() {
        return artifacts;
    }

    /**
     * Walks a directory and builds the list of artifacts
     * it contains.
     * @param repoPath the path to the repository
     * @return the repository view
     */
    public static PythonMavenRepository inspect(Path repoPath) {
        var groups = repoPath.toFile().listFiles(File::isDirectory);
        var mavenArtifacts=  Arrays.stream(groups)
            .flatMap(group -> {
               var artifacts = group.listFiles(File::isDirectory);
               return Arrays.stream(artifacts)
                   .flatMap(artifact -> {
                       var versions = artifact.listFiles(File::isDirectory);
                       return Arrays.stream(versions).flatMap(version -> {
                           var baseName = artifact.getName() + "-" + version.getName();
                           var files = version.listFiles();
                           if (files != null) {
                               for (var file : files) {
                                   var fileName = file.getName();
                                   if (fileName.startsWith(baseName) && (fileName.endsWith(".pom") || fileName.endsWith(".jar"))) {
                                       return Stream.of(new MavenArtifact(group.getName(), artifact.getName(), version.getName()));
                                   }
                               }
                           }
                           return Stream.of();
                       });
                   });
            }).toList();
        return new PythonMavenRepository(repoPath, mavenArtifacts);
    }

    public List<File> asClasspath() {
        if (Files.isDirectory(repositoryPath)) {
            try (var walker = Files.walk(repositoryPath)) {
                return walker.map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".jar"))
                    .toList();
            } catch (IOException ex) {
                return List.of();
            }
        }
        return List.of();
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }
}
