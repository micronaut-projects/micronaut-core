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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The incremental flow every embedded compiler shares: which sources to compile, hiding what they
 * and the deleted sources produced before, staging the outputs, and replacing the previous outputs
 * as one transaction on success. A compiler implements {@link #runCompilation} only.
 *
 * <p>An incremental request recompiles the changed sources and every source whose classes reference
 * a changed or deleted class, as the {@link ClassDependencyIndex} of the current output tells. Class
 * files and generated sources go to staging directories that replace the outputs only when the
 * compilation succeeded, in one {@link OutputTransaction}: a failed edit leaves the running
 * generation intact, deletions included, and a failed promotion is rolled back. Which top-level
 * classes a source produces is learnt from the compiler and kept beside the class output (see
 * {@link SourceIndex}), so a source declaring several top-level classes is handled whole.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public abstract class StagedSourceCompiler implements SourceCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(StagedSourceCompiler.class);
    private static final String STAGING_SUFFIX = ".micronaut-dev-staging";
    private static final String BACKUP_SUFFIX = ".micronaut-dev-backup";
    private static final String STAGED_MAPPING_SUFFIX = ".staged";

    /**
     * Runs the compiler on the selected sources.
     *
     * @param request The request
     * @param toCompile The sources to compile, absolute
     * @param hiddenClasses The top-level classes the compiler must not resolve from the class output: those
     *                      of deleted sources and the previous outputs of the sources being compiled
     * @param staging Where class files and resources go
     * @param generatedStaging Where generated sources go
     * @param diagnostics Where to add the compiler's messages
     * @return What was produced, by source, or null when the compilation failed
     * @throws IOException if the compiler cannot read or write
     */
    @Nullable
    protected abstract Produced runCompilation(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException;

    /**
     * Whether a batch that selects no source to compile, one that only deletes sources, still runs
     * {@link #runCompilation}, with an empty set: a compiler whose processors keep state between runs and
     * regenerate what depends on every source, as KSP does, needs to see the deletion, and what the run
     * produces replaces what the deleted sources produced. By default such a batch only removes.
     *
     * @return True to run the compilation for an empty batch
     */
    protected boolean compilesEmptyBatches() {
        return false;
    }

    @Override
    public CompilationResult compile(CompilationRequest request) {
        long start = System.nanoTime();
        Path classOutput = request.classOutput();
        SourceIndex sources = SourceIndex.scan(request.sourceRoots(), classOutput, request.kind());
        Set<String> deletedClasses = new LinkedHashSet<>();
        for (Path deleted : request.deleted()) {
            deletedClasses.addAll(sources.classesOf(deleted));
        }
        Set<Path> toCompile = selectSources(request, sources, deletedClasses);
        if (toCompile.isEmpty() && !compilesEmptyBatches()) {
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
            // what the compiled sources produced before is hidden too: the compiler has their sources, and a class a
            // source no longer declares must not be resolved from its old class file by a dependent
            Set<String> hiddenClasses = new LinkedHashSet<>(deletedClasses);
            for (Path source : toCompile) {
                hiddenClasses.addAll(sources.classesOf(source));
            }
            if (request.isFull()) {
                // everything this language produced before is hidden, the class of a source deleted unnoticed
                // included; what another language wrote into a shared output stays visible
                for (Path source : sources.recordedSources()) {
                    hiddenClasses.addAll(sources.classesOf(source));
                    hiddenClasses.addAll(sources.recordedClassesOf(source));
                }
            }
            Produced produced = runCompilation(request, toCompile, hiddenClasses, staging, generatedStaging, diagnostics);
            if (produced == null) {
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
            Path stagedMapping = stagedMapping(classOutput, request.kind());
            sources.recordProduced(stagedMapping, produced.classes(), produced.generatedSources(), produced.resources(), toCompile, request.deleted(), request.isFull());
            Set<Path> removed = promote(request, outputsToRemove, staging, generatedStaging, stagedMapping);
            Set<String> compiledClasses = new LinkedHashSet<>(deletedClasses);
            produced.classes().values().forEach(compiledClasses::addAll);
            for (Path source : replaced) {
                // the sources compiled, the deleted ones, and on a full run those that went unnoticed: all their
                // classes changed as far as another language is concerned
                compiledClasses.addAll(sources.classesOf(source));
                compiledClasses.addAll(sources.recordedClassesOf(source));
            }
            return new CompilationResult(CompilationResult.Status.SUCCESS, diagnostics, toCompile, removed, elapsed(start), compiledClasses);
        } catch (IOException | UncheckedIOException e) {
            diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "Compilation failed: " + e.getMessage(), null, 0, 0));
            return new CompilationResult(CompilationResult.Status.FAILED, diagnostics, toCompile, Set.of(), elapsed(start));
        } finally {
            for (Path directory : List.of(staging, generatedStaging, stagedMapping(classOutput, request.kind()))) {
                try {
                    OutputTransaction.deleteRecursively(directory);
                } catch (IOException e) {
                    LOG.debug("Cannot delete staging directory {}", directory, e);
                }
            }
        }
    }

    /**
     * The staging directory beside an output directory.
     *
     * @param output The output directory
     * @return Its staging directory
     */
    protected static Path staging(Path output) {
        return output.resolveSibling(output.getFileName() + STAGING_SUFFIX);
    }

    private static Path stagedMapping(Path classOutput, SourceKind kind) {
        Path mapping = SourceIndex.mappingFile(classOutput, kind);
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
                    transaction.remove(SourceIndex.mappingFile(classOutput, request.kind()));
                } else {
                    for (Path output : outputsOf(request, sources, request.deleted(), deletedClasses)) {
                        transaction.remove(output);
                    }
                    if (!deletedClasses.isEmpty()) {
                        Path stagedMapping = stagedMapping(classOutput, request.kind());
                        sources.recordProduced(stagedMapping, Map.of(), Map.of(), Map.of(), Set.of(), request.deleted(), false);
                        transaction.put(stagedMapping, SourceIndex.mappingFile(classOutput, request.kind()));
                    }
                }
                transaction.commit();
            } catch (IOException | UncheckedIOException e) {
                transaction.rollback();
                throw e;
            }
            Set<Path> removed = new LinkedHashSet<>(transaction.removed());
            removed.removeIf(Files::exists);
            // the deleted classes are what changed: another language's dependents of them are recompiled
            return new CompilationResult(CompilationResult.Status.NOTHING_TO_DO, List.of(), Set.of(), removed, elapsed(start), deletedClasses);
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
            transaction.put(stagedMapping, SourceIndex.mappingFile(classOutput, request.kind()));
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
        // what another language changed in this batch: the dependents among these sources go too
        affectedClasses.addAll(request.affectedClasses());
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
     *
     * @param classOutput The class output directory
     * @param className The top-level class, by binary name
     * @return The files, which may not exist
     */
    protected static Set<Path> outputsOf(Path classOutput, String className) {
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
     *
     * @param fileName The output file name
     * @param name The class name, simple for a class file and qualified for an index entry
     * @param suffix The suffix the file name must carry, {@code .class} or nothing
     * @return True if the file belongs to the class
     */
    protected static boolean belongsTo(String fileName, String name, String suffix) {
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

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /**
     * What a compilation produced, by the source it was produced for.
     *
     * @param classes The top-level classes written, by source
     * @param generatedSources The generated sources, relative to the generated sources directory, by source
     * @param resources The resources written into the class output, relative to it, by source
     */
    public record Produced(Map<Path, Set<String>> classes, Map<Path, Set<String>> generatedSources, Map<Path, Set<String>> resources) {

        /**
         * Validating constructor.
         *
         * @param classes The classes
         * @param generatedSources The generated sources
         * @param resources The resources
         */
        public Produced {
            classes = Map.copyOf(Objects.requireNonNull(classes, "classes"));
            generatedSources = Map.copyOf(Objects.requireNonNull(generatedSources, "generatedSources"));
            resources = Map.copyOf(Objects.requireNonNull(resources, "resources"));
        }
    }
}
