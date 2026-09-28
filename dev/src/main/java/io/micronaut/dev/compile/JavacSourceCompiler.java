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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
public final class JavacSourceCompiler implements SourceCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(JavacSourceCompiler.class);
    private static final String STAGING_SUFFIX = ".micronaut-dev-staging";
    private static final String BACKUP_SUFFIX = ".micronaut-dev-backup";
    private static final String STAGED_MAPPING_SUFFIX = ".staged";

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
    public CompilationResult compile(CompilationRequest request) {
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler in this JVM: development mode needs a JDK, not a JRE");
        }
        long start = System.nanoTime();
        Path classOutput = request.classOutput();
        SourceIndex sources = SourceIndex.scan(request.sourceRoots(), classOutput);
        Set<String> deletedClasses = new LinkedHashSet<>();
        for (Path deleted : request.deleted()) {
            deletedClasses.addAll(sources.classesOf(deleted));
        }
        Set<Path> toCompile = selectSources(request, sources, deletedClasses);
        if (toCompile.isEmpty()) {
            return removeOnly(request, sources, deletedClasses, start);
        }
        Path staging = staging(classOutput);
        Path generatedStaging = staging(request.generatedSources());
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        try {
            OutputTransaction.deleteRecursively(staging);
            OutputTransaction.deleteRecursively(generatedStaging);
            Files.createDirectories(staging);
            Files.createDirectories(generatedStaging);
            // what the compiled sources produced before is hidden too: javac has their sources, and a class a
            // source no longer declares must not be resolved from its old class file by a dependent
            Set<String> hiddenClasses = new LinkedHashSet<>(deletedClasses);
            for (Path source : toCompile) {
                hiddenClasses.addAll(sources.classesOf(source));
            }
            StagingFileManager fileManager = runJavac(request, toCompile, hiddenClasses, staging, generatedStaging, diagnostics);
            if (fileManager == null) {
                // the outputs are exactly as they were: no deletion applied, nothing promoted
                return new CompilationResult(CompilationResult.Status.FAILED, diagnostics, toCompile, Set.of(), elapsed(start));
            }
            // what goes is read from the mapping before the mapping is rewritten for what came
            Set<Path> replaced = new LinkedHashSet<>(toCompile);
            replaced.addAll(request.deleted());
            Set<Path> outputsToRemove;
            if (request.isFull()) {
                // a full compilation produces everything again: whatever it produced before and does not
                // produce now, the class of a source deleted while the compiler was not watching included, is stale
                replaced.addAll(sources.recordedSources());
                outputsToRemove = recordedOutputsOf(request, sources, replaced);
            } else {
                outputsToRemove = outputsOf(request, sources, replaced, hiddenClasses);
            }
            Path stagedMapping = stagedMapping(classOutput);
            sources.recordProduced(stagedMapping, fileManager.produced(generatedStaging), fileManager.producedSources(generatedStaging), fileManager.producedResources(staging), toCompile, request.deleted(), request.isFull());
            Set<Path> removed = promote(request, outputsToRemove, staging, generatedStaging, stagedMapping);
            return new CompilationResult(CompilationResult.Status.SUCCESS, diagnostics, toCompile, removed, elapsed(start));
        } catch (IOException | UncheckedIOException e) {
            diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "Compilation failed: " + e.getMessage(), null, 0, 0));
            return new CompilationResult(CompilationResult.Status.FAILED, diagnostics, toCompile, Set.of(), elapsed(start));
        } finally {
            for (Path directory : List.of(staging, generatedStaging, stagedMapping(classOutput))) {
                try {
                    OutputTransaction.deleteRecursively(directory);
                } catch (IOException e) {
                    LOG.debug("Cannot delete staging directory {}", directory, e);
                }
            }
        }
    }

    private static Path staging(Path output) {
        return output.resolveSibling(output.getFileName() + STAGING_SUFFIX);
    }

    private static Path stagedMapping(Path classOutput) {
        Path mapping = SourceIndex.mappingFile(classOutput);
        return mapping.resolveSibling(mapping.getFileName() + STAGED_MAPPING_SUFFIX);
    }

    /**
     * Nothing to compile: the outputs of the deleted sources go, or every compiler output when a full
     * compilation found no source at all, as one transaction.
     */
    private static CompilationResult removeOnly(CompilationRequest request, SourceIndex sources, Set<String> deletedClasses, long start) {
        Path classOutput = request.classOutput();
        try {
            OutputTransaction transaction = new OutputTransaction(classOutput.resolveSibling(classOutput.getFileName() + BACKUP_SUFFIX));
            try {
                if (request.isFull()) {
                    // no source left: everything the embedded compiler ever produced goes
                    for (Path output : recordedOutputsOf(request, sources, sources.recordedSources())) {
                        transaction.remove(output);
                    }
                    removeAll(transaction, request.generatedSources(), file -> true);
                    transaction.remove(SourceIndex.mappingFile(classOutput));
                } else {
                    for (Path output : outputsOf(request, sources, request.deleted(), deletedClasses)) {
                        transaction.remove(output);
                    }
                    if (!deletedClasses.isEmpty()) {
                        Path stagedMapping = stagedMapping(classOutput);
                        sources.recordProduced(stagedMapping, Map.of(), Map.of(), Map.of(), Set.of(), request.deleted(), false);
                        transaction.put(stagedMapping, SourceIndex.mappingFile(classOutput));
                    }
                }
                transaction.commit();
            } catch (IOException | UncheckedIOException e) {
                transaction.rollback();
                throw e;
            }
            Set<Path> removed = new LinkedHashSet<>(transaction.removed());
            removed.removeIf(Files::exists);
            return new CompilationResult(CompilationResult.Status.NOTHING_TO_DO, List.of(), Set.of(), removed, elapsed(start));
        } catch (IOException | UncheckedIOException e) {
            CompileDiagnostic diagnostic = new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "Cannot remove the outputs of deleted sources: " + e.getMessage(), null, 0, 0);
            return new CompilationResult(CompilationResult.Status.FAILED, List.of(diagnostic), Set.of(), Set.of(), elapsed(start));
        }
    }

    /**
     * Replaces the outputs: what the deleted and the recompiled sources produced goes, or for a full
     * compilation everything the embedded compiler ever produced, then the staged class files,
     * generated sources and source mapping come in, all of it or none of it.
     *
     * <p>A full compilation removes the outputs recorded in the mapping and those of the sources it
     * compiles, not every class file in the output: another language may share the directory. A class
     * the build tool compiled whose source went away before the embedded compiler ever saw it is the
     * one case this leaves behind, until the build tool cleans it.</p>
     */
    private static Set<Path> promote(CompilationRequest request, Set<Path> outputsToRemove, Path staging, Path generatedStaging, Path stagedMapping) throws IOException {
        Path classOutput = request.classOutput();
        OutputTransaction transaction = new OutputTransaction(classOutput.resolveSibling(classOutput.getFileName() + BACKUP_SUFFIX));
        try {
            for (Path output : outputsToRemove) {
                transaction.remove(output);
            }
            if (request.isFull()) {
                removeAll(transaction, request.generatedSources(), file -> true);
            }
            transaction.promote(staging, classOutput);
            transaction.promote(generatedStaging, request.generatedSources());
            transaction.put(stagedMapping, SourceIndex.mappingFile(classOutput));
            transaction.commit();
        } catch (IOException | UncheckedIOException e) {
            transaction.rollback();
            throw e;
        }
        Set<Path> removed = new LinkedHashSet<>(transaction.removed());
        removed.removeIf(Files::exists);
        return removed;
    }

    /**
     * What the given sources produced: the outputs of their classes, the sources generated for them
     * and the resources written for them.
     */
    private static Set<Path> outputsOf(CompilationRequest request, SourceIndex sources, Set<Path> replaced, Set<String> replacedClasses) {
        Path classOutput = request.classOutput();
        Set<Path> outputs = new LinkedHashSet<>();
        for (String className : replacedClasses) {
            outputs.addAll(outputsOf(classOutput, className));
        }
        for (Path source : replaced) {
            for (String generated : sources.generatedOf(source)) {
                outputs.add(request.generatedSources().resolve(generated));
            }
            for (String resource : sources.resourcesOf(source)) {
                outputs.add(classOutput.resolve(resource));
            }
        }
        return outputs;
    }

    private static Set<Path> recordedOutputsOf(CompilationRequest request, SourceIndex sources, Set<Path> replaced) {
        Set<String> classes = new LinkedHashSet<>();
        for (Path source : replaced) {
            classes.addAll(sources.classesOf(source));
            classes.addAll(sources.recordedClassesOf(source));
        }
        return outputsOf(request, sources, replaced, classes);
    }

    private static void removeAll(OutputTransaction transaction, Path directory, Predicate<Path> which) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(which).toList()) {
                transaction.remove(file);
            }
        }
    }

    /**
     * The sources of an incremental request: the changed ones and, through the dependency index of the
     * current output, every source whose classes reference a changed or deleted class.
     */
    private static Set<Path> selectSources(CompilationRequest request, SourceIndex sources, Set<String> deletedClasses) {
        if (request.isFull()) {
            return sources.allSources();
        }
        Set<Path> selected = new LinkedHashSet<>();
        Set<String> affectedClasses = new LinkedHashSet<>(deletedClasses);
        for (Path changed : request.changed()) {
            if (Files.isRegularFile(changed)) {
                selected.add(changed.toAbsolutePath().normalize());
                affectedClasses.addAll(sources.classesOf(changed));
            }
        }
        if (!affectedClasses.isEmpty()) {
            ClassDependencyIndex index = ClassDependencyIndex.scan(request.classOutput());
            for (String affected : affectedClasses) {
                if (index.declaresConstants(affected)) {
                    // javac inlined its constants into whoever read them, leaving no reference to follow:
                    // every source is recompiled rather than guessing which
                    LOG.debug("{} declares compile-time constants: recompiling every source", affected);
                    return sources.allSources();
                }
            }
            for (String dependent : index.transitiveDependentsOf(affectedClasses)) {
                sources.sourceOf(dependent).ifPresent(selected::add);
            }
        }
        return selected;
    }

    /**
     * What the output holds for a top-level class: its class files and those of its nested classes,
     * the classes Micronaut generated for it, and its entries in the {@code META-INF/micronaut} index.
     */
    static Set<Path> outputsOf(Path classOutput, String className) {
        Set<Path> outputs = new LinkedHashSet<>();
        if (!Files.isDirectory(classOutput)) {
            return outputs;
        }
        int lastDot = className.lastIndexOf('.');
        String packagePath = lastDot < 0 ? "" : className.substring(0, lastDot).replace('.', File.separatorChar);
        String simpleName = lastDot < 0 ? className : className.substring(lastDot + 1);
        Path packageDir = classOutput.resolve(packagePath);
        if (Files.isDirectory(packageDir)) {
            try (Stream<Path> files = Files.list(packageDir)) {
                files.filter(file -> belongsTo(file.getFileName().toString(), simpleName, ".class")).forEach(outputs::add);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot list " + packageDir, e);
            }
        }
        Path index = classOutput.resolve("META-INF").resolve("micronaut");
        if (Files.isDirectory(index)) {
            try (Stream<Path> services = Files.list(index)) {
                services.filter(Files::isDirectory).forEach(service -> {
                    try (Stream<Path> entries = Files.list(service)) {
                        entries.filter(entry -> belongsTo(entry.getFileName().toString(), className, "")).forEach(outputs::add);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Cannot list " + service, e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot list " + index, e);
            }
        }
        return outputs;
    }

    /**
     * Whether an output file name belongs to a class: {@code Foo.class}, {@code Foo$Inner.class},
     * {@code $Foo$Definition.class}, {@code Foo$Intercepted.class}, and the same shapes for index entries.
     */
    static boolean belongsTo(String fileName, String name, String suffix) {
        if (!fileName.endsWith(suffix)) {
            return false;
        }
        String base = fileName.substring(0, fileName.length() - suffix.length());
        int lastDot = name.lastIndexOf('.');
        String prefix = lastDot < 0 ? "" : name.substring(0, lastDot + 1);
        String simple = lastDot < 0 ? name : name.substring(lastDot + 1);
        if (!base.startsWith(prefix)) {
            return false;
        }
        String rest = base.substring(prefix.length());
        if (rest.startsWith("$")) {
            rest = rest.substring(1);
        }
        return rest.equals(simple) || rest.startsWith(simple + "$");
    }

    /**
     * Runs javac.
     *
     * @return The file manager, which knows what was produced, or null when the compilation failed
     */
    @Nullable
    private StagingFileManager runJavac(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException {
        JavaCompiler javac = compiler;
        if (javac == null) {
            throw new IllegalStateException("No Java compiler");
        }
        try (StandardJavaFileManager standard = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8);
             StagingFileManager fileManager = new StagingFileManager(standard, hiddenClasses)) {
            List<Path> classPath = new ArrayList<>(request.compileClasspath());
            if (!request.isFull() && Files.isDirectory(request.classOutput())) {
                // an incremental compilation resolves the unchanged classes from the output; a full one
                // compiles every source and must not see a class whose source went away unnoticed
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
            return Boolean.TRUE.equals(task.call()) ? fileManager : null;
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

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
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
