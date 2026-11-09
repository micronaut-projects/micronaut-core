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
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
 * several sources goes when one of them compiles, so all of them compile: only they can write it again. A source the
 * index has no record of compiles with every source credited with a resource of the class output that two or more
 * sources are credited with, since it may add to what a processor aggregates from them.</p>
 *
 * <p>A compiler that {@linkplain #comparesStructureFirst() compares the structure first} compiles the changed sources
 * alone, and their dependents only when a class they produced differs in structure from the one in the output: a
 * change to method bodies alone leaves the dependents' classes as they were.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public abstract class StagedSourceCompiler implements SourceCompiler {

    /**
     * The system property that, set to {@code false} on the development JVM, compiles every edit with the processors:
     * no edit of method bodies compiles without them (see {@link #runWithoutProcessors}).
     */
    static final String BODY_EDITS_PROPERTY = "micronaut.dev.compile.body-edits-without-processors";

    private static final Logger LOG = LoggerFactory.getLogger(StagedSourceCompiler.class);
    private static final String STAGING_SUFFIX = ".micronaut-dev-staging";
    private static final String BACKUP_SUFFIX = ".micronaut-dev-backup";
    private static final String STAGED_MAPPING_SUFFIX = ".staged";
    private static final String MICRONAUT_INDEX = "META-INF/micronaut/";
    private static final String CLASS_SUFFIX = ".class";
    private static final long HASH_MULTIPLIER = 0x9E3779B97F4A7C15L;

    /**
     * The changed sources whose last compilation changed the structure of their classes: the next edit of one is likely to
     * do so again, as a repository's queries or an entity's fields are edited, so it compiles with its dependents at once.
     */
    private final Set<Path> structureChanged = ConcurrentHashMap.newKeySet();

    /**
     * What the processors read of each source that its classes do not hold (see {@link #declarationText}), as it was when
     * the source last compiled: an edit that leaves it and the structure of the classes as they were leaves the
     * processors' input as it was.
     */
    private final Map<Path, String> declarationTexts = new ConcurrentHashMap<>();

    /**
     * What the processors ran with when the texts were recorded: the processor path, the options and the compile
     * classpath, with the stamps of their jars and class directories. Under another, the processors' outputs may differ
     * for an unchanged source, and the texts are forgotten.
     */
    private final AtomicReference<@Nullable List<Object>> processorConfiguration = new AtomicReference<>();

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

    /**
     * Whether an incremental request compiles the changed sources alone first, and their dependents only if a class
     * they produced differs in structure from the one in the output (see {@link #keepsStructure}). That holds for a
     * compiler whose output for a source depends on the classes it uses through their structure alone: not for one
     * that inlines the bodies of methods into their callers, as Kotlin does for an inline function. By default the
     * dependents compile with the changed sources.
     *
     * @return True to compile the changed sources alone first
     */
    protected boolean comparesStructureFirst() {
        return false;
    }

    /**
     * Compiles sources without the annotation or symbol processors, for {@link #compile} to keep what the processors
     * produced for them before when every class compiled differs from the one in the output in method bodies alone and
     * {@link #declarationText} is as it was: the processors read declarations, annotations, signatures and
     * documentation, none of which such an edit changes. By default there is no such mode.
     *
     * @param request The request
     * @param toCompile The sources to compile, absolute
     * @param hiddenClasses The top-level classes the compiler must not resolve from the class output: those compiled
     *                      from the sources themselves. What the processors produced for them stays visible, as it is
     *                      kept, so a source that uses a class generated from it compiles, and a generated top-level
     *                      declaration stays listed in the module metadata
     * @param staging Where class files go
     * @param diagnostics Where to add the compiler's messages
     * @return What was compiled, by source, or null when the compilation failed or the compiler has no such mode
     * @throws IOException if the compiler cannot read or write
     */
    protected @Nullable Produced runWithoutProcessors(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, List<CompileDiagnostic> diagnostics) throws IOException {
        return null;
    }

    /**
     * Whether the compiler compiles an edit of method bodies without the processors: it implements
     * {@link #runWithoutProcessors} and {@link #declarationText}, and there are processors to leave out. By default it
     * does not. The system property {@value #BODY_EDITS_PROPERTY} set to {@code false} turns it off whatever this says.
     *
     * @return True if an edit of method bodies may compile without the processors
     */
    protected boolean compilesBodyEditsWithoutProcessors() {
        return false;
    }

    /**
     * What the processors can read of a source that the class files compiled from it do not hold, as its documentation
     * and its annotations of {@code SOURCE} retention: null, by default, when {@link #runWithoutProcessors} is not
     * supported.
     *
     * @param source The text of a source
     * @return That part of it, or null
     */
    protected @Nullable String declarationText(String source) {
        return null;
    }

    @Override
    public CompilationResult compile(CompilationRequest request) {
        long start = System.nanoTime();
        if (bodyEditsWithoutProcessors()) {
            // stamped only for a compiler that records texts: the class directories are walked
            List<Object> configuration = List.of(request.processorPath(), request.options(), request.compileClasspath(),
                stamps(request.processorPath(), request), stamps(request.compileClasspath(), request));
            if (!configuration.equals(processorConfiguration.getAndSet(configuration))) {
                declarationTexts.clear();
            }
        } else {
            declarationTexts.clear();
            processorConfiguration.set(null);
        }
        Path classOutput = request.classOutput();
        SourceIndex sources = SourceIndex.scan(request.sourceRoots(), classOutput, request.kind());
        Set<String> deletedClasses = new LinkedHashSet<>();
        for (Path deleted : request.deleted()) {
            deletedClasses.addAll(sources.classesOf(deleted));
        }
        Set<Path> aggregating = aggregatingWithNewSources(request, sources);
        Set<Path> toCompile = selectSources(request, sources, deletedClasses, aggregating);
        if (toCompile.isEmpty() && !compilesEmptyBatches()) {
            return removeOnly(request, sources, deletedClasses, start);
        }
        // a new source compiled alone stages classes the output lacks, and anything aggregated without the other sources:
        // the structure is never kept, so the attempt would be wasted
        Map<Path, String> bodies = aggregating.isEmpty() ? bodyEditCandidates(request, sources) : null;
        if (bodies != null) {
            CompilationResult kept = compileSources(request, sources, deletedClasses, bodies.keySet(), start, true, bodies, false);
            if (kept != null) {
                LOG.debug("Compiled {} changed source(s) without the processors: their classes changed in method bodies alone, so the processors' outputs and {} dependent source(s) stay as they were", bodies.size(), toCompile.size() - bodies.size());
                return kept;
            }
            LOG.debug("The changed sources changed the structure of their classes, or failed to compile without the processors: compiling {} source(s) with the processors", toCompile.size());
        }
        Set<Path> changed = aggregating.isEmpty() ? changedFirst(request, sources, toCompile) : null;
        if (changed != null) {
            CompilationResult alone = compileSources(request, sources, deletedClasses, changed, start, true, null, false);
            if (alone != null) {
                LOG.debug("Compiled {} changed source(s) alone: the structure of their classes is the same, so {} dependent source(s) did not compile", changed.size(), toCompile.size() - changed.size());
                return alone;
            }
            LOG.debug("The changed sources changed the structure of their classes, or failed to compile alone: compiling {} source(s) with their dependents", toCompile.size());
        }
        return Objects.requireNonNull(compileSources(request, sources, deletedClasses, toCompile, start, false, null, changed != null || bodies != null));
    }

    /**
     * Compiles the given sources, and replaces the outputs with what they produced.
     *
     * @param onlyIfStructureKept Whether to give up, leaving the outputs as they were, when the compilation fails or a
     *                            class it produced differs in structure from the one in the output (see {@link #keepsStructure})
     * @param checkedTexts The {@link #declarationText} of each source, as {@link #bodyEditCandidates} found it the same as
     *                     recorded, to compile without the processors and keep what they produced (see
     *                     {@link #keptProcessorOutputs}), giving up as when the structure is not kept; null to compile
     *                     with the processors
     * @param aloneRejected Whether the changed sources of the request were compiled alone first, with the processors or
     *                      without, and that was given up
     * @return What was compiled, or null if given up
     */
    private @Nullable CompilationResult compileSources(CompilationRequest request, SourceIndex sources, Set<String> deletedClasses, Set<Path> toCompile, long start, boolean onlyIfStructureKept, @Nullable Map<Path, String> checkedTexts, boolean aloneRejected) {
        Path classOutput = request.classOutput();
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
            // read before the compiler reads the sources: an edit after this compiles again, and is compared with this.
            // Without the processors, the texts checked are recorded, not read again: an edit saved since the check, of
            // the documentation say, which the class files do not show, then differs from them and goes through the processors
            Map<Path, String> texts = checkedTexts != null ? checkedTexts : declarationTexts(toCompile);
            Produced produced = checkedTexts != null
                ? keptProcessorOutputs(request, sources, toCompile, hiddenClasses, staging, generatedStaging)
                : runCompilation(request, toCompile, hiddenClasses, staging, generatedStaging, diagnostics);
            if (onlyIfStructureKept && (produced == null || !keepsStructure(request, sources, toCompile, hiddenClasses, produced, staging, generatedStaging))) {
                // a failure compiles again with the dependents, which report their own errors too
                structureChanged.addAll(toCompile);
                return null;
            }
            if (onlyIfStructureKept) {
                structureChanged.removeAll(toCompile);
            } else if (produced != null && comparesStructureFirst() && !request.isFull()) {
                learnStructure(request, sources, toCompile, produced, staging, aloneRejected);
            } else if (produced != null && !texts.isEmpty() && !request.isFull()) {
                learnBodyEdits(request, produced, staging, aloneRejected);
            }
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
            if (request.isFull()) {
                declarationTexts.clear();
            }
            request.deleted().forEach(deleted -> declarationTexts.remove(deleted.toAbsolutePath().normalize()));
            declarationTexts.putAll(texts);
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
            if (onlyIfStructureKept) {
                // the outputs are as they were, a failed promotion rolled back: the dependents compile too
                LOG.debug("Compiling the changed sources alone failed", e);
                return null;
            }
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
     * current output, every source whose classes reference a changed or deleted class, the sources a new source
     * compiles with (see {@link #aggregatingWithNewSources}), and then every source that shares an output with one
     * selected or deleted.
     */
    private Set<Path> selectSources(CompilationRequest request, SourceIndex sources, Set<String> deletedClasses, Set<Path> aggregating) {
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
        selected.addAll(aggregating);
        withSharedOutputs(request, sources, selected);
        return selected;
    }

    /**
     * The sources a changed source the index has no record of compiles with: every source credited with a resource of
     * the class output that two or more sources are credited with. A processor writes such a resource again from the
     * sources one compilation holds. A new source compiled alone would leave out what the others contributed: the
     * processor would replace the resource, or write one under another name and leave the old one as it was. Nothing
     * tells which processor writes what, so a new source compiles with the sources of every such resource.
     *
     * <p>None for a full request, for one without a new source, and for a compiler whose processors keep state between
     * runs and write again themselves what they aggregate (see {@link #compilesEmptyBatches}).</p>
     */
    private Set<Path> aggregatingWithNewSources(CompilationRequest request, SourceIndex sources) {
        if (request.isFull() || compilesEmptyBatches()) {
            return Set.of();
        }
        // a source the build tool compiled has no record either until it compiles here; its classes in the output do not
        // tell it from a new one, since its first edit here may be what makes it contribute to an aggregate
        List<Path> added = request.changed().stream().filter(file -> Files.isRegularFile(file) && !sources.isRecorded(file)).toList();
        if (added.isEmpty()) {
            return Set.of();
        }
        Set<Path> aggregating = new LinkedHashSet<>();
        for (Path source : sources.sourcesOfSharedResources()) {
            // a deleted source is no regular file
            if (Files.isRegularFile(source)) {
                aggregating.add(source);
            }
        }
        if (!aggregating.isEmpty()) {
            LOG.debug("{} has no record of what it produced: compiling it with the {} source(s) credited with a resource that several sources are credited with", added, aggregating.size());
        }
        return aggregating;
    }

    /**
     * Adds to the selected sources every source that shares an output with one selected or deleted.
     */
    private void withSharedOutputs(CompilationRequest request, SourceIndex sources, Set<Path> selected) {
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
            LOG.debug("{} source(s) share an output with the {} selected and {} deleted: a group of {} source(s) compiles together", selected.size() - before, before, request.deleted().size(), selected.size());
        }
    }

    /**
     * The sources an incremental request compiles first, alone: the changed ones and those sharing an output with them,
     * when the selected sources are more than that. None for a full request, one that deletes a source, whose classes
     * the dependents may no longer link against, one that another language's changes take part in, whose dependents
     * here are not the changed sources', or one that compiles the source of another module of the build.
     *
     * @return The sources, or null to compile every selected source at once
     */
    private @Nullable Set<Path> changedFirst(CompilationRequest request, SourceIndex sources, Set<Path> toCompile) {
        if (!comparesStructureFirst() || request.isFull() || !request.deleted().isEmpty() || !request.affectedClasses().isEmpty()) {
            return null;
        }
        Set<Path> changed = new LinkedHashSet<>();
        for (Path file : request.changed()) {
            if (Files.isRegularFile(file)) {
                changed.add(file.toAbsolutePath().normalize());
            }
        }
        withSharedOutputs(request, sources, changed);
        SiblingOutput siblings = request.siblingOutput();
        if (changed.isEmpty() || changed.size() >= toCompile.size() || siblings != null && changed.stream().anyMatch(siblings::owns)) {
            return null;
        }
        if (changed.stream().anyMatch(structureChanged::contains)) {
            LOG.debug("The last compilation of a changed source changed the structure of its classes: compiling its dependents with it");
            return null;
        }
        return changed;
    }

    /**
     * Whether what the changed sources compiled alone produced can replace what they produced before without their
     * dependents compiling again: the compilation compiled no other source, every file it staged in the class output is
     * there already, a class with the same structure (see {@link DependentStructure}) and a resource with the same bytes,
     * every source it generated exists already, its bytes not compared since the classes compiled from it are, and every
     * output the sources had before is staged again. The structure holds every member, signature, annotation,
     * constant value and nested class, which is all javac reads of a class it compiles a dependent against.
     *
     * <p>What the class file does not hold is not compared: the comments of the changed sources and their
     * annotations of source retention. A processor that reads those from a source compiled with its dependents
     * now sees them only once the dependent compiles; what it aggregates from several sources is written again whole, and
     * differs, so the dependents compile.</p>
     */
    private static boolean keepsStructure(CompilationRequest request, SourceIndex sources, Set<Path> compiled, Set<String> hiddenClasses, Produced produced, Path staging, Path generatedStaging) {
        Set<Path> credited = new LinkedHashSet<>(produced.classes().keySet());
        credited.addAll(produced.generatedSources().keySet());
        credited.addAll(produced.resources().keySet());
        Path generatedStaged = canonical(generatedStaging);
        for (Path source : credited) {
            // what was generated from a source the processors generated for the changed ones goes with them
            if (!compiled.contains(source.toAbsolutePath().normalize()) && !canonical(source).startsWith(generatedStaged)) {
                // a source found on the source path compiled with them: the outputs may lag behind it
                LOG.debug("Compiling the changed sources alone compiled {} too", source);
                return false;
            }
        }
        Path classOutput = request.classOutput();
        Path generatedSources = request.generatedSources();
        try {
            Set<Path> staged = new LinkedHashSet<>();
            for (Path file : regularFiles(staging)) {
                Path existing = classOutput.resolve(staging.relativize(file).toString());
                staged.add(existing);
                if (!Files.isRegularFile(existing) || !sameStructure(existing, file)) {
                    LOG.debug("{} is new or differs from the output", existing);
                    return false;
                }
            }
            for (Path file : regularFiles(generatedStaging)) {
                Path existing = generatedSources.resolve(generatedStaging.relativize(file).toString());
                staged.add(existing);
                if (!Files.isRegularFile(existing)) {
                    LOG.debug("{} is a new generated source", existing);
                    return false;
                }
            }
            for (Path output : outputsOf(request, sources, compiled, hiddenClasses)) {
                if (Files.isRegularFile(output) && !staged.contains(output)) {
                    // an output they no longer produce
                    LOG.debug("{} is no longer produced", output);
                    return false;
                }
            }
            return true;
        } catch (IOException | UncheckedIOException e) {
            LOG.debug("Cannot compare what the changed sources produced with the output", e);
            return false;
        }
    }

    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /**
     * Notes, for each changed source a compilation with its dependents compiled, whether the structure of its top-level
     * classes changed: which way the next edit of it compiles first. It only predicts, from the class files javac compiled
     * from the source itself, so it costs little; what the changed sources compile to alone is always checked whole. A
     * compilation without dependents has nothing to predict.
     *
     * <p>When the alone attempt of the same request was given up, a source whose classes kept their structure stays
     * marked: the attempt failed for something the classes do not show, a resource a processor aggregates, a new
     * generated source or an output no longer produced, which the next edit is as likely to repeat.</p>
     */
    private void learnStructure(CompilationRequest request, SourceIndex sources, Set<Path> toCompile, Produced produced, Path staging, boolean aloneRejected) {
        if (request.changed().stream().filter(Files::isRegularFile).count() >= toCompile.size()) {
            return;
        }
        for (Path file : request.changed()) {
            Path source = file.toAbsolutePath().normalize();
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Set<String> classes = new LinkedHashSet<>(sources.classesOf(source));
            if (!sources.isRecorded(source) && classes.stream().noneMatch(className -> Files.isRegularFile(request.classOutput().resolve(classFile(className))))) {
                // a new source: no class of it was in the output to compare with
                continue;
            }
            classes.addAll(produced.classes().getOrDefault(source, Set.of()));
            boolean kept = true;
            try {
                for (String className : classes) {
                    String classFile = classFile(className);
                    Path stagedFile = staging.resolve(classFile);
                    Path existing = request.classOutput().resolve(classFile);
                    kept &= Files.isRegularFile(stagedFile) && Files.isRegularFile(existing) && sameStructure(existing, stagedFile);
                }
            } catch (IOException | UncheckedIOException e) {
                kept = false;
            }
            if (kept) {
                if (!aloneRejected) {
                    structureChanged.remove(source);
                }
            } else {
                structureChanged.add(source);
            }
        }
    }

    private static String classFile(String className) {
        return className.replace('.', File.separatorChar) + CLASS_SUFFIX;
    }

    private static boolean sameStructure(Path existing, Path staged) throws IOException {
        byte[] before = Files.readAllBytes(existing);
        byte[] after = Files.readAllBytes(staged);
        if (staged.getFileName().toString().endsWith(CLASS_SUFFIX)) {
            return DependentStructure.same(before, after);
        }
        return Arrays.equals(before, after);
    }

    private boolean bodyEditsWithoutProcessors() {
        return compilesBodyEditsWithoutProcessors() && Boolean.parseBoolean(System.getProperty(BODY_EDITS_PROPERTY, "true").trim());
    }

    /**
     * When and how large each file of a path is, a jar rebuilt in place included, and for a directory of classes, as
     * another project of the build compiles into, the same of every file it holds, summed: a class or an annotation
     * changed there may change what the processors generate for an unchanged source. The class outputs of the request
     * are left out, as a compilation here changes them.
     */
    private static List<String> stamps(List<Path> path, CompilationRequest request) {
        Set<Path> outputs = new HashSet<>();
        outputs.add(request.classOutput().toAbsolutePath().normalize());
        if (request.siblingOutput() != null) {
            outputs.add(request.siblingOutput().classes().toAbsolutePath().normalize());
        }
        List<String> stamps = new ArrayList<>(path.size());
        for (Path entry : path) {
            try {
                if (Files.isRegularFile(entry)) {
                    stamps.add(Files.getLastModifiedTime(entry).toMillis() + ":" + Files.size(entry));
                } else if (Files.isDirectory(entry) && !outputs.contains(entry.toAbsolutePath().normalize())) {
                    stamps.add(directoryStamp(entry));
                }
            } catch (IOException | UncheckedIOException e) {
                stamps.add(entry + ":unreadable");
            }
        }
        return stamps;
    }

    /**
     * The files of a directory, how many there are and a sum over each one's name, time and size, which a file added,
     * removed or rewritten changes: no file is read, and the sum does not depend on the order of the walk.
     */
    private static String directoryStamp(Path directory) throws IOException {
        long[] stamp = new long[2];
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    long name = directory.relativize(file).toString().hashCode();
                    stamp[0]++;
                    stamp[1] += (name * HASH_MULTIPLIER) ^ (attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) * 31 + attributes.size());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return directory + ":" + stamp[0] + ":" + stamp[1];
    }

    /**
     * The {@link #declarationText} of each source, read now; empty when the compiler has none, or body edits do not
     * compile without the processors.
     */
    private Map<Path, String> declarationTexts(Set<Path> toCompile) {
        if (!bodyEditsWithoutProcessors()) {
            return Map.of();
        }
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path source : toCompile) {
            try {
                String text = declarationText(Files.readString(source, StandardCharsets.UTF_8));
                if (text == null) {
                    return Map.of();
                }
                texts.put(source.toAbsolutePath().normalize(), text);
            } catch (IOException | UncheckedIOException e) {
                // unreadable now: nothing is known of it, and its next edit goes through the processors
                LOG.debug("Cannot read {}", source, e);
            }
        }
        return texts;
    }

    /**
     * The changed sources to compile without the processors first (see {@link #runWithoutProcessors}): null unless the
     * compiler supports it and the request is incremental, deletes nothing and has no sibling output; unless each changed
     * source compiled before in this session with the same {@link #declarationText}, and the last compilation of none
     * changed the structure of its classes; and when the code of a class of one is inlined into other classes, which a
     * change to a body changes.
     *
     * @return The {@link #declarationText} of each source, as read now, for {@link #compileSources} to record
     */
    private @Nullable Map<Path, String> bodyEditCandidates(CompilationRequest request, SourceIndex sources) {
        // nothing compiled in this session, or a compiler without the mode, which records no text
        if (declarationTexts.isEmpty() || request.isFull() || !request.deleted().isEmpty() || !request.affectedClasses().isEmpty() || request.siblingOutput() != null) {
            return null;
        }
        Set<Path> changed = new LinkedHashSet<>();
        for (Path file : request.changed()) {
            if (Files.isRegularFile(file)) {
                changed.add(file.toAbsolutePath().normalize());
            }
        }
        withSharedOutputs(request, sources, changed);
        if (changed.isEmpty() || changed.stream().anyMatch(structureChanged::contains)) {
            return null;
        }
        Map<Path, String> texts = declarationTexts(changed);
        for (Path source : changed) {
            String before = declarationTexts.get(source);
            if (before == null || !before.equals(texts.get(source))) {
                // never compiled in this session, so what the processors read of it before is not known, or changed since
                return null;
            }
        }
        ClassDependencyIndex index = ClassDependencyIndex.scan(request.classOutput());
        for (Path source : changed) {
            for (String className : sources.classesOf(source)) {
                if (index.isInlinedElsewhere(className)) {
                    LOG.debug("The code of {} is inlined into other classes: compiling them with the processors", className);
                    return null;
                }
            }
        }
        return texts;
    }

    /**
     * Compiles the sources without the processors and, when each class compiled differs from the one in the output in
     * method bodies alone, stages again, unchanged, what the processors produced for the sources before.
     *
     * <p>The classes are compared as {@link DependentStructure} does: the line numbers of a static initializer, which a
     * line added above a companion object's or a top-level property moves, do not count, its code does.</p>
     *
     * @return What was produced, or null when a class is new or changed in structure, a class of the sources is no longer
     *         produced, or the compilation failed: the sources then compile with the processors
     */
    private @Nullable Produced keptProcessorOutputs(CompilationRequest request, SourceIndex sources, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging) throws IOException {
        Path classOutput = request.classOutput();
        // what the processors produced is kept, so the compiler sees it: a source that uses a class generated from it
        // compiles, and the module metadata keeps what they generated listed
        Set<String> ownClasses = new LinkedHashSet<>();
        for (Path source : toCompile) {
            String fileName = source.getFileName().toString();
            for (String className : sources.classesOf(source)) {
                Path classFile = classOutput.resolve(classFile(className));
                if (!Files.isRegularFile(classFile) || fileName.equals(sourceFileOf(classFile))) {
                    ownClasses.add(className);
                }
            }
        }
        ownClasses.retainAll(hiddenClasses);
        // the diagnostics of a failure are reported by the compilation with the processors that follows
        Produced compiled = runWithoutProcessors(request, toCompile, ownClasses, staging, new ArrayList<>());
        if (compiled == null) {
            return null;
        }
        for (Path file : regularFiles(staging)) {
            String relative = staging.relativize(file).toString();
            if (!relative.endsWith(CLASS_SUFFIX)) {
                continue;
            }
            Path existing = classOutput.resolve(relative);
            if (!Files.isRegularFile(existing) || !sameStructure(existing, file)) {
                LOG.debug("{} is new or changed in structure: compiling with the processors", relative);
                return null;
            }
        }
        Set<String> compiledClasses = new HashSet<>();
        compiled.classes().values().forEach(compiledClasses::addAll);
        Map<Path, Set<String>> classes = new LinkedHashMap<>();
        compiled.classes().forEach((source, names) -> classes.put(source, new LinkedHashSet<>(names)));
        Map<Path, Set<String>> generatedSources = new LinkedHashMap<>();
        Map<Path, Set<String>> resources = new LinkedHashMap<>();
        for (Path source : toCompile) {
            String fileName = source.getFileName().toString();
            for (String className : sources.classesOf(source)) {
                // what the output holds of the class, with what the processors generated for it: what the compilation did
                // not stage again is the processors', unless it is a class of the source itself, which it no longer produces
                boolean kept = false;
                for (Path output : outputsOf(classOutput, className)) {
                    if (!Files.isRegularFile(output) || Files.exists(staging.resolve(classOutput.relativize(output).toString()))) {
                        continue;
                    }
                    if (output.toString().endsWith(CLASS_SUFFIX) && fileName.equals(sourceFileOf(output))) {
                        LOG.debug("{} is no longer produced: compiling with the processors", output);
                        return null;
                    }
                    copyInto(classOutput, output, staging);
                    kept = true;
                }
                if (kept && !compiledClasses.contains(className)) {
                    classes.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(className);
                }
            }
            for (String resource : sources.resourcesOf(source)) {
                copyInto(classOutput, classOutput.resolve(resource), staging);
                resources.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(resource);
            }
            for (String generated : sources.generatedOf(source)) {
                copyInto(request.generatedSources(), request.generatedSources().resolve(generated), generatedStaging);
                generatedSources.computeIfAbsent(source, s -> new LinkedHashSet<>()).add(generated);
            }
        }
        return new Produced(classes, generatedSources, resources);
    }

    /**
     * After a compilation with the processors, whether each changed source changed only method bodies of its classes, as
     * its next edit then likely does too: the next edit of one that changed their structure compiles with the processors
     * at once, rather than first without them in vain.
     */
    private void learnBodyEdits(CompilationRequest request, Produced produced, Path staging, boolean aloneRejected) {
        for (Path file : request.changed()) {
            Path source = file.toAbsolutePath().normalize();
            if (!Files.isRegularFile(source)) {
                continue;
            }
            String fileName = source.getFileName().toString();
            boolean bodies = true;
            try {
                for (String className : produced.classes().getOrDefault(source, Set.of())) {
                    for (Path stagedFile : outputsOf(staging, className)) {
                        // the source's own classes: what the processors generated is compared by the reload
                        if (!stagedFile.toString().endsWith(CLASS_SUFFIX) || !fileName.equals(sourceFileOf(stagedFile))) {
                            continue;
                        }
                        Path existing = request.classOutput().resolve(staging.relativize(stagedFile).toString());
                        bodies &= Files.isRegularFile(existing) && sameStructure(existing, stagedFile);
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                bodies = false;
            }
            if (bodies) {
                if (!aloneRejected) {
                    // one rejected without the processors in this request stays marked: the rejection is what counts
                    structureChanged.remove(source);
                }
            } else {
                structureChanged.add(source);
            }
        }
    }

    private static void copyInto(Path root, Path file, Path staging) throws IOException {
        if (Files.isRegularFile(file)) {
            Path target = staging.resolve(root.relativize(file).toString());
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static @Nullable String sourceFileOf(Path classFile) throws IOException {
        try {
            return ClassFile.of().parse(Files.readAllBytes(classFile))
                .findAttribute(Attributes.sourceFile())
                .map(attribute -> attribute.sourceFile().stringValue())
                .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Path> regularFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).toList();
        }
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
