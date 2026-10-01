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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The embedded Java compiler: the JDK's javac through the compiler API, compiling incrementally.
 *
 * <p>An incremental request recompiles the changed sources and every source whose classes reference
 * a changed or deleted class, as the {@link ClassDependencyIndex} of the current output tells; the
 * deleted classes are hidden from the compilation, so a dependent of a deleted class fails to compile
 * rather than link against a class about to go. The compilation runs against the compile classpath
 * plus the current output, so unchanged classes resolve from their class files, and writes class
 * files and generated sources into staging directories that replace the outputs only when the
 * compilation succeeded, in one {@link OutputTransaction}: a failed edit leaves the running
 * generation intact, deletions included, and a failed promotion is rolled back. On success the
 * previous outputs of every recompiled source are removed before the staged ones are promoted, so a
 * nested class or a generated companion a source no longer produces does not survive, and deleted
 * sources have their class files, the classes generated for them and their entries in the
 * {@code META-INF/micronaut} index removed.</p>
 *
 * <p>Which top-level classes a source produces is learnt from javac and kept beside the class output
 * (see {@link SourceIndex}), so a source declaring several top-level classes is handled whole.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class JavacSourceCompiler extends StagedSourceCompiler {

    @Nullable
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    @Override
    public Set<SourceKind> kinds() {
        return Set.of(SourceKind.JAVA);
    }

    @Override
    public boolean isAvailable() {
        return compiler != null;
    }

    @Override
    @Nullable
    protected Produced runCompilation(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException {
        JavaCompiler javac = compiler;
        if (javac == null) {
            throw new IllegalStateException("No Java compiler in this JVM: development mode needs a JDK, not a JRE");
        }
        try (StandardJavaFileManager standard = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8);
             StagingFileManager fileManager = new StagingFileManager(standard, hiddenClasses)) {
            List<Path> classPath = new ArrayList<>(request.compileClasspath());
            if (Files.isDirectory(request.classOutput())) {
                // the unchanged classes, and what another language wrote into a shared output, resolve from the
                // output; what this compilation must not see, its own previous outputs on a full run included,
                // the file manager hides
                classPath.add(request.classOutput());
            }
            standard.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
            standard.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(staging));
            standard.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedStaging));
            standard.setLocationFromPaths(StandardLocation.SOURCE_PATH, request.sourceRoots().stream().map(SourceRoot::path).toList());
            if (!request.processorPath().isEmpty()) {
                standard.setLocationFromPaths(StandardLocation.ANNOTATION_PROCESSOR_PATH, request.processorPath());
            }
            List<String> options = new ArrayList<>(request.options());
            if (!options.contains("-implicit:none") && !options.contains("-implicit:class")) {
                // a source found on the source path for a referenced class is compiled too, so that the
                // dependency closure the index computed is complete even where the index lagged
                options.add("-implicit:class");
            }
            Iterable<? extends JavaFileObject> units = standard.getJavaFileObjectsFromPaths(toCompile);
            JavaCompiler.CompilationTask task = javac.getTask(null, fileManager, diagnostic -> diagnostics.add(toDiagnostic(diagnostic)), options, null, units);
            if (!Boolean.TRUE.equals(task.call())) {
                return null;
            }
            return new Produced(fileManager.produced(generatedStaging), fileManager.producedSources(generatedStaging), fileManager.producedResources(staging));
        }
    }

    private static CompileDiagnostic toDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        CompileDiagnostic.Severity severity = switch (diagnostic.getKind()) {
            case ERROR -> CompileDiagnostic.Severity.ERROR;
            case WARNING, MANDATORY_WARNING -> CompileDiagnostic.Severity.WARNING;
            default -> CompileDiagnostic.Severity.NOTE;
        };
        JavaFileObject source = diagnostic.getSource();
        Path file = null;
        if (source != null) {
            try {
                file = Path.of(source.toUri());
            } catch (RuntimeException e) {
                file = null;
            }
        }
        return new CompileDiagnostic(severity, diagnostic.getMessage(null), file, diagnostic.getLineNumber(), diagnostic.getColumnNumber());
    }

    /**
     * The file manager of a compilation: hides the classes of deleted sources from the class path, so
     * that nothing links against them, and notes which source each class file written was compiled from.
     */
    private static final class StagingFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Set<String> hiddenTopLevelClasses;
        private final Map<Path, Set<String>> produced = new HashMap<>();
        private final Map<Path, Set<Path>> producedSources = new HashMap<>();
        private final Map<Path, Set<Path>> producedResources = new HashMap<>();

        StagingFileManager(StandardJavaFileManager fileManager, Set<String> hiddenTopLevelClasses) {
            super(fileManager);
            this.hiddenTopLevelClasses = hiddenTopLevelClasses;
        }

        /**
         * @return The top-level classes written, by the source they were compiled from or generated for
         */
        Map<Path, Set<String>> produced(Path generatedStaging) {
            // a class compiled from a generated source is credited by javac to that staged source; it is
            // credited here to the source the generated one was generated for, so that it goes with it
            Path staged = canonical(generatedStaging);
            Map<Path, Path> originOfGenerated = new HashMap<>();
            for (Map.Entry<Path, Set<Path>> entry : producedSources.entrySet()) {
                for (Path generated : entry.getValue()) {
                    originOfGenerated.put(canonical(generated), entry.getKey());
                }
            }
            Map<Path, Set<String>> ownSources = new HashMap<>();
            for (Map.Entry<Path, Set<String>> entry : produced.entrySet()) {
                Path key = canonical(entry.getKey());
                if (key.startsWith(staged)) {
                    Path origin = originOfGenerated.get(key);
                    if (origin != null) {
                        ownSources.computeIfAbsent(origin, s -> new LinkedHashSet<>()).addAll(entry.getValue());
                    }
                } else {
                    ownSources.computeIfAbsent(entry.getKey(), s -> new LinkedHashSet<>()).addAll(entry.getValue());
                }
            }
            return ownSources;
        }

        /**
         * @param generatedSources The staged generated sources directory the paths are relative to
         * @return The generated sources written, relative to that directory, by the source they were generated for
         */
        Map<Path, Set<String>> producedSources(Path generatedSources) {
            return relativize(producedSources, generatedSources);
        }

        /**
         * @param classOutput The staged class output the paths are relative to
         * @return The resources the processors wrote into the class output, relative to it, by the source they were written for
         */
        Map<Path, Set<String>> producedResources(Path classOutput) {
            return relativize(producedResources, classOutput);
        }

        private static Map<Path, Set<String>> relativize(Map<Path, Set<Path>> files, Path directory) {
            // javac names its outputs by their real path, which on a system with a symbolic link in the
            // temporary directory is not the path the request named
            Path base = canonical(directory);
            Map<Path, Set<String>> relative = new HashMap<>();
            for (Map.Entry<Path, Set<Path>> entry : files.entrySet()) {
                Set<String> names = new LinkedHashSet<>();
                for (Path file : entry.getValue()) {
                    Path real = canonical(file);
                    if (real.startsWith(base)) {
                        names.add(base.relativize(real).toString());
                    }
                }
                if (!names.isEmpty()) {
                    relative.put(entry.getKey(), names);
                }
            }
            return relative;
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
            Iterable<JavaFileObject> listed = super.list(location, packageName, kinds, recurse);
            if (hiddenTopLevelClasses.isEmpty() || location != StandardLocation.CLASS_PATH) {
                return listed;
            }
            List<JavaFileObject> visible = new ArrayList<>();
            for (JavaFileObject file : listed) {
                if (file.getKind() != JavaFileObject.Kind.CLASS || !isHidden(inferBinaryName(location, file))) {
                    visible.add(file);
                }
            }
            return visible;
        }

        @Override
        @Nullable
        public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {
            if (location == StandardLocation.CLASS_PATH && kind == JavaFileObject.Kind.CLASS && isHidden(className)) {
                return null;
            }
            return super.getJavaFileForInput(location, className, kind);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, @Nullable FileObject sibling) throws IOException {
            JavaFileObject output = super.getJavaFileForOutput(location, className, kind, sibling);
            if (sibling != null) {
                attribute(location, className, kind, output, sibling);
            }
            return output;
        }

        @Override
        public JavaFileObject getJavaFileForOutputForOriginatingFiles(Location location, String className, JavaFileObject.Kind kind, FileObject... originatingFiles) throws IOException {
            // javac writes its class files and the processors their generated files through here, naming
            // the sources they originate from; each is credited with the output, so that the output goes
            // when the source is recompiled without producing it, or is deleted
            FileObject sibling = originatingFiles.length > 0 ? originatingFiles[0] : null;
            JavaFileObject output = super.getJavaFileForOutput(location, className, kind, sibling);
            for (FileObject originating : originatingFiles) {
                attribute(location, className, kind, output, originating);
            }
            return output;
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName, String relativeName, @Nullable FileObject sibling) throws IOException {
            FileObject output = super.getFileForOutput(location, packageName, relativeName, sibling);
            if (sibling != null) {
                attributeResource(location, output, sibling);
            }
            return output;
        }

        @Override
        public FileObject getFileForOutputForOriginatingFiles(Location location, String packageName, String relativeName, FileObject... originatingFiles) throws IOException {
            FileObject sibling = originatingFiles.length > 0 ? originatingFiles[0] : null;
            FileObject output = super.getFileForOutput(location, packageName, relativeName, sibling);
            for (FileObject originating : originatingFiles) {
                attributeResource(location, output, originating);
            }
            return output;
        }

        private void attributeResource(Location location, FileObject output, FileObject originating) {
            if (location != StandardLocation.CLASS_OUTPUT) {
                return;
            }
            try {
                Path source = Path.of(originating.toUri()).toAbsolutePath().normalize();
                producedResources.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(Path.of(output.toUri()).toAbsolutePath().normalize());
            } catch (RuntimeException e) {
                // not files on both ends: nothing to remove later
            }
        }

        private void attribute(Location location, String className, JavaFileObject.Kind kind, JavaFileObject output, FileObject originating) {
            Path source;
            try {
                source = Path.of(originating.toUri()).toAbsolutePath().normalize();
            } catch (RuntimeException e) {
                // not a file: a class generated from another generated class, attributed by name instead
                return;
            }
            if (location == StandardLocation.CLASS_OUTPUT && kind == JavaFileObject.Kind.CLASS) {
                produced.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(ClassDependencyIndex.topLevelOf(className));
            } else if (location == StandardLocation.SOURCE_OUTPUT) {
                try {
                    producedSources.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(Path.of(output.toUri()).toAbsolutePath().normalize());
                } catch (RuntimeException e) {
                    // a generated source that is not a file cannot be removed later either
                }
            }
        }

        private static Path canonical(Path path) {
            try {
                return path.toRealPath();
            } catch (IOException e) {
                return path.toAbsolutePath().normalize();
            }
        }

        private boolean isHidden(@Nullable String binaryName) {
            return binaryName != null && hiddenTopLevelClasses.contains(ClassDependencyIndex.topLevelOf(binaryName));
        }
    }
}
