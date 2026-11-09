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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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
 * {@link SourceIndex}), so a source declaring several top-level classes is handled whole. An output credited to
 * several sources goes when one of them compiles, so all of them compile: only they can write it again.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public abstract class StagedSourceCompiler implements SourceCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(StagedSourceCompiler.class);
    private static final String STAGING_SUFFIX = ".micronaut-dev-staging";
    private static final String BACKUP_SUFFIX = ".micronaut-dev-backup";
    private static final String STAGED_MAPPING_SUFFIX = ".staged";
    private static final String MICRONAUT_INDEX = "META-INF/micronaut/";
    private static final String CLASS_SUFFIX = ".class";

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
        SiblingOutput siblings = request.siblingOutput();
        List<Path> stagingDirectories = new ArrayList<>(List.of(staging, generatedStaging, stagedMapping(classOutput, request.kind())));
        if (siblings != null) {
            stagingDirectories.add(staging(siblings.classes()));
            stagingDirectories.add(staging(siblings.generatedSources(request.kind())));
        }
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        try {
            for (Path directory : stagingDirectories) {
                OutputTransaction.deleteRecursively(directory);
            }
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
            if (siblings != null) {
                outputsToRemove = new LinkedHashSet<>(outputsToRemove);
                outputsToRemove.addAll(routeToSiblingOutput(request, siblings, sources, toCompile, hiddenClasses, produced, staging, generatedStaging));
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
            for (Path directory : stagingDirectories) {
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
                    if (request.siblingOutput() != null) {
                        removeAll(transaction, request.siblingOutput().generatedSources(request.kind()), file -> true);
                    }
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
                transaction.rollback(e);
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
            SiblingOutput siblings = request.siblingOutput();
            if (request.isFull()) {
                removeAll(transaction, request.generatedSources(), file -> true);
                if (siblings != null) {
                    removeAll(transaction, siblings.generatedSources(request.kind()), file -> true);
                }
            }
            transaction.promote(staging, classOutput);
            transaction.promote(generatedStaging, request.generatedSources());
            if (siblings != null) {
                transaction.promote(staging(siblings.classes()), siblings.classes());
                transaction.promote(staging(siblings.generatedSources(request.kind())), siblings.generatedSources(request.kind()));
            }
            transaction.put(stagedMapping, SourceIndex.mappingFile(classOutput, request.kind()));
            transaction.commit();
        } catch (IOException | UncheckedIOException e) {
            transaction.rollback(e);
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
        SiblingOutput siblings = request.siblingOutput();
        for (String className : replacedClasses) {
            outputs.addAll(outputsOf(classOutput, className));
            if (siblings != null) {
                outputs.addAll(outputsOf(siblings.classes(), className));
            }
        }
        for (Path source : replaced) {
            for (String generated : sources.generatedOf(source)) {
                outputs.add(request.generatedSources().resolve(generated));
                if (siblings != null) {
                    outputs.add(siblings.generatedSources(request.kind()).resolve(generated));
                }
            }
            for (String resource : sources.resourcesOf(source)) {
                outputs.add(classOutput.resolve(resource));
                if (siblings != null) {
                    outputs.add(siblings.classes().resolve(resource));
                }
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
     * current output, every source whose classes reference a changed or deleted class, and then every source that
     * shares an output with one selected or deleted.
     */
    private Set<Path> selectSources(CompilationRequest request, SourceIndex sources, Set<String> deletedClasses) {
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
            SiblingOutput siblings = request.siblingOutput();
            ClassDependencyIndex index = siblings == null ? ClassDependencyIndex.scan(request.classOutput())
                : ClassDependencyIndex.scan(List.of(siblings.classes(), request.classOutput()));
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
        // what these sources produced is about to go, and a class generated from several sources goes with each of them;
        // processors that keep state write again themselves what they aggregate, so only the source a class belongs to is needed
        int before = selected.size();
        Deque<Path> pending = new ArrayDeque<>(selected);
        pending.addAll(request.deleted());
        while (!pending.isEmpty()) {
            Path source = pending.poll();
            for (Path sharing : compilesEmptyBatches() ? sources.ownSourcesOfClassesOf(source) : sources.sourcesSharingOutputsWith(source)) {
                if (Files.isRegularFile(sharing) && !request.deleted().contains(sharing) && selected.add(sharing)) {
                    pending.add(sharing);
                }
            }
        }
        if (selected.size() > before) {
            // the group has no limit: every subclass of a base class with injection points, credited with it, compiles
            LOG.debug("{} source(s) share an output with the {} selected: a group of {} source(s) compiles together", selected.size() - before, before, selected.size());
        }
        return selected;
    }

    /**
     * Moves what the compilation produced for the sources of other modules from the staging directories to those of the
     * {@link SiblingOutput}, so that nothing of theirs is promoted into the application's outputs. One compilation has
     * both kinds of source, since a change in a module recompiles what depends on it: a file goes with the source that
     * produced it, as the compiler credits it or as its name tells, {@code $Foo$Definition.class} and its entry in
     * {@code META-INF/micronaut} going with {@code Foo}. A file nothing tells the owner of goes with the siblings' if one
     * of them was compiled at all: it may hold what they produced, and the directory is read first, so nothing is lost.
     *
     * @return What must go from the sibling output, not to shadow what is about to be promoted to the application's outputs
     */
    private Set<Path> routeToSiblingOutput(CompilationRequest request, SiblingOutput siblings, SourceIndex sources, Set<Path> toCompile, Set<String> hiddenClasses, Produced produced, Path staging, Path generatedStaging) throws IOException {
        Set<String> siblingClasses = new HashSet<>();
        Set<String> ownClasses = new HashSet<>();
        for (Path source : toCompile) {
            (siblings.owns(source) ? siblingClasses : ownClasses).addAll(sources.classesOf(source));
        }
        boolean siblingCompiled = !siblingClasses.isEmpty();
        for (Map.Entry<Path, Set<String>> entry : produced.classes().entrySet()) {
            boolean sibling = siblings.owns(entry.getKey());
            siblingCompiled |= sibling;
            (sibling ? siblingClasses : ownClasses).addAll(entry.getValue());
        }
        Set<String> kept = keepSharedOutputs(request, siblings, hiddenClasses, staging, staging(siblings.classes()), siblingClasses);
        if (siblingCompiled) {
            Set<String> siblingResources = credited(produced.resources(), siblings, true);
            Set<String> ownResources = credited(produced.resources(), siblings, false);
            move(staging, staging(siblings.classes()), relative -> {
                if (kept.contains(relative) || ownResources.contains(relative) && !siblingResources.contains(relative)) {
                    return false;
                }
                String owner = classOfOutput(relative);
                return siblingResources.contains(relative) || owner == null || !ownClasses.contains(owner) || siblingClasses.contains(owner);
            });
            Set<String> ownGenerated = credited(produced.generatedSources(), siblings, false);
            Set<String> siblingGenerated = credited(produced.generatedSources(), siblings, true);
            move(generatedStaging, staging(siblings.generatedSources(request.kind())), relative -> siblingGenerated.contains(relative) || !ownGenerated.contains(relative));
        }
        Set<Path> shadowing = new LinkedHashSet<>();
        if (Files.isDirectory(staging)) {
            try (Stream<Path> files = Files.walk(staging)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String relative = staging.relativize(file).toString();
                    if (!kept.contains(relative)) {
                        shadowing.add(siblings.classes().resolve(relative));
                    }
                }
            }
        }
        return shadowing;
    }

    /**
     * Splits between the staging directories, and merges with what the outputs hold, a file whose contents belong to more
     * than one source, which a compiler that writes such a file knows how to do.
     *
     * @param request The request
     * @param siblings The sibling output
     * @param hiddenClasses The classes the compilation replaced
     * @param staging The staging directory of the class output
     * @param siblingStaging The staging directory of the sibling output
     * @param siblingClasses The top-level classes of the compiled sibling sources
     * @return The files, relative to the staging directories, that are in place
     * @throws IOException if a file cannot be read or written
     */
    protected Set<String> keepSharedOutputs(CompilationRequest request, SiblingOutput siblings, Set<String> hiddenClasses, Path staging, Path siblingStaging, Set<String> siblingClasses) throws IOException {
        return Set.of();
    }

    private static Set<String> credited(Map<Path, Set<String>> files, SiblingOutput siblings, boolean sibling) {
        Set<String> credited = new HashSet<>();
        files.forEach((source, names) -> {
            if (siblings.owns(source) == sibling) {
                credited.addAll(names);
            }
        });
        return credited;
    }

    /**
     * The top-level class an output file belongs to by its name: a class file's, or that of an entry of the
     * {@code META-INF/micronaut} index.
     */
    private static @Nullable String classOfOutput(String relative) {
        String path = relative.replace(File.separatorChar, '/');
        if (path.endsWith(CLASS_SUFFIX)) {
            return ClassDependencyIndex.topLevelOf(path.substring(0, path.length() - CLASS_SUFFIX.length()).replace('/', '.'));
        }
        if (path.startsWith(MICRONAUT_INDEX)) {
            return ClassDependencyIndex.topLevelOf(path.substring(path.lastIndexOf('/') + 1));
        }
        return null;
    }

    private static void move(Path staging, Path target, Predicate<String> which) throws IOException {
        if (!Files.isDirectory(staging)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(staging)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        for (Path file : files) {
            String relative = staging.relativize(file).toString();
            if (which.test(relative)) {
                Path moved = target.resolve(relative);
                Files.createDirectories(Objects.requireNonNull(moved.getParent()));
                Files.move(file, moved);
            }
        }
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
                files.filter(file -> belongsTo(file.getFileName().toString(), simpleName, CLASS_SUFFIX)).forEach(outputs::add);
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
