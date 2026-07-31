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

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Associates javac outputs with the source file supplied as their sibling.
 */
final class TrackingJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Path targetDirectory;
    private final Map<String, Set<String>> outputs = new LinkedHashMap<>();
    private final Set<String> pythonGeneratedOutputs = new LinkedHashSet<>();
    private Function<JavaFileObject, String> sourceResolver = TrackingJavaFileManager::fileSourceKey;
    private Predicate<JavaFileObject> pythonSourceResolver = ignored -> false;

    TrackingJavaFileManager(StandardJavaFileManager fileManager, Path targetDirectory) {
        super(fileManager);
        Path normalizedTarget;
        try {
            normalizedTarget = targetDirectory.toRealPath().normalize();
        } catch (IOException e) {
            normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        }
        this.targetDirectory = normalizedTarget;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location,
                                               String className,
                                               JavaFileObject.Kind kind,
                                               FileObject sibling) throws IOException {
        JavaFileObject output = super.getJavaFileForOutput(location, className, kind, sibling);
        trackOutput(sibling, output);
        return output;
    }

    @Override
    public FileObject getFileForOutput(JavaFileManager.Location location,
                                       String packageName,
                                       String relativeName,
                                       FileObject sibling) throws IOException {
        FileObject output = super.getFileForOutput(location, packageName, relativeName, sibling);
        trackOutput(sibling, output);
        return output;
    }

    Map<String, Set<String>> outputs() {
        return outputs;
    }

    Set<String> pythonGeneratedOutputs() {
        return pythonGeneratedOutputs;
    }

    Path targetDirectory() {
        return targetDirectory;
    }

    void setSourceResolver(Function<JavaFileObject, String> sourceResolver,
                           Predicate<JavaFileObject> pythonSourceResolver) {
        this.sourceResolver = sourceResolver;
        this.pythonSourceResolver = pythonSourceResolver;
    }

    private void trackOutput(FileObject sibling, FileObject output) {
        if (!(sibling instanceof JavaFileObject javaSibling)) {
            return;
        }
        String source = sourceResolver.apply(javaSibling);
        if (source == null) {
            return;
        }
        try {
            Path outputPath = Path.of(output.toUri()).toAbsolutePath().normalize();
            if (outputPath.startsWith(targetDirectory)) {
                String relative = targetDirectory.relativize(outputPath).toString()
                    .replace(outputPath.getFileSystem().getSeparator(), "/");
                outputs.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(relative);
                if (pythonSourceResolver.test(javaSibling)) {
                    pythonGeneratedOutputs.add(relative);
                }
            }
        } catch (Exception ignored) {
            // Outputs that cannot be attributed are handled conservatively as aggregating.
        }
    }

    private static String fileSourceKey(JavaFileObject source) {
        try {
            Path path = Path.of(source.toUri());
            return java.nio.file.Files.exists(path)
                ? path.toRealPath().normalize().toString()
                : path.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
