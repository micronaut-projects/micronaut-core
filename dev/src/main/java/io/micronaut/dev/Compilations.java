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
package io.micronaut.dev;

import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.compile.CompilationRequest;
import io.micronaut.dev.compile.CompilationResult;
import io.micronaut.dev.compile.CompileMode;
import io.micronaut.dev.compile.OutputLock;
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.manifest.DevManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a compilation of a language of a {@link DevRuntime}'s manifest reads: its roots and options, with those of the
 * languages it compiles jointly, and which languages the runtime compiles at all.
 */
@Internal
final class Compilations {

    // the runtime's logger: what is logged here is logged as the runtime's
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final int MAX_PROPAGATION_PASSES = 5;

    private Compilations() {
    }

    /**
     * The roots a compilation of a language reads: its own, and for Kotlin the Java roots too, since kotlinc
     * resolves the Java sources of a mixed module and KSP processes them, and those of the languages it compiles
     * jointly; the index of a request takes only the roots of its own language as its sources.
     */
    static List<SourceRoot> compilationRoots(DevManifest manifest, SourceKind kind, Map<SourceKind, SourceKind> jointOwners) {
        List<SourceRoot> roots = new ArrayList<>(manifest.sourceRoots(kind));
        if (kind == SourceKind.KOTLIN) {
            roots.addAll(manifest.sourceRoots(SourceKind.JAVA));
        }
        jointOwners.forEach((joint, owner) -> {
            if (owner == kind) {
                roots.addAll(manifest.sourceRoots(joint));
            }
        });
        return roots;
    }

    /**
     * The options of a compilation of a language: its own, then those of each language it compiles jointly,
     * since one compiler run compiles them all. A language's options are kept whole, a flag with its value,
     * and are not repeated when they are the same as the owner's.
     */
    static List<String> compileOptions(DevManifest manifest, SourceKind kind, Map<SourceKind, SourceKind> jointOwners) {
        List<String> own = manifest.compileOptions(kind);
        List<String> options = new ArrayList<>(own);
        jointOwners.forEach((joint, owner) -> {
            List<String> jointOptions = manifest.compileOptions(joint);
            if (owner == kind && !jointOptions.equals(own)) {
                options.addAll(jointOptions);
            }
        });
        return options;
    }

    /**
     * The languages a compiler of another language compiles jointly (see {@link SourceCompiler#jointKinds()}):
     * those it names that have sources, are compiled in this JVM and share its class output.
     *
     * @param manifest The manifest
     * @param compilers The compilers by language
     * @return The owning language, by jointly compiled language
     */
    static Map<SourceKind, SourceKind> jointOwners(DevManifest manifest, Map<SourceKind, SourceCompiler> compilers) {
        Map<SourceKind, SourceKind> owners = new EnumMap<>(SourceKind.class);
        compilers.forEach((kind, compiler) -> {
            if (!embedded(manifest, kind)) {
                return;
            }
            for (SourceKind joint : compiler.jointKinds()) {
                if (joint != kind && embedded(manifest, joint) && manifest.classOutput(joint).equals(manifest.classOutput(kind))) {
                    owners.putIfAbsent(joint, kind);
                }
            }
        });
        return owners;
    }

    /**
     * The lock of the application's class outputs, which every development process of the project takes, development
     * mode and test mode alike: a file beside the first of the outputs its compilers write, by name, so that each
     * process finds the same one from its own manifest, whose application part is the same. None when the build tool
     * compiles every language, and nothing here writes the outputs.
     *
     * @param manifest The manifest, the application's and not its tests' view
     * @return The lock
     */
    static OutputLock outputLock(DevManifest manifest) {
        TreeSet<Path> outputs = new TreeSet<>();
        for (SourceKind kind : SourceKind.values()) {
            if (!manifest.sourceRoots(kind).isEmpty() && manifest.compileMode(kind) != CompileMode.BUILD_TOOL) {
                outputs.add(manifest.classOutput(kind).toAbsolutePath().normalize());
            }
        }
        if (outputs.isEmpty()) {
            return OutputLock.none();
        }
        Path first = outputs.first();
        return OutputLock.at(first.resolveSibling(first.getFileName() + ".micronaut-dev.lock"));
    }

    static boolean embedded(DevManifest manifest, SourceKind kind) {
        return !manifest.sourceRoots(kind).isEmpty() && manifest.compileMode(kind) != CompileMode.BUILD_TOOL;
    }

    /**
     * Compiles in full every language whose class output does not exist yet: see
     * {@link DevRuntime#compileMissingOutputs(DevManifest, Map)}.
     *
     * @param manifest The manifest
     * @param compilers The compilers by language
     * @throws IllegalStateException if a compilation fails
     */
    static void compileMissingOutputs(DevManifest manifest, Map<SourceKind, SourceCompiler> compilers) {
        Map<SourceKind, SourceKind> jointOwners = jointOwners(manifest, compilers);
        // decided before anything is compiled: two languages sharing one output are both missing, or neither
        Set<SourceKind> missing = new LinkedHashSet<>();
        for (SourceKind kind : compilers.keySet()) {
            if (!jointOwners.containsKey(kind) && embedded(manifest, kind) && !Files.isDirectory(manifest.classOutput(kind))) {
                missing.add(kind);
            }
        }
        for (Map.Entry<SourceKind, SourceCompiler> entry : compilers.entrySet()) {
            SourceKind kind = entry.getKey();
            if (!missing.contains(kind)) {
                continue;
            }
            CompilationRequest request = new CompilationRequest(kind, compilationRoots(manifest, kind, jointOwners), Set.of(), Set.of(), true, manifest.compileClasspath(),
                manifest.processorPath(), manifest.classOutput(kind), manifest.generatedSources(kind), compileOptions(manifest, kind, jointOwners)).asFull();
            CompilationResult result = entry.getValue().compile(request);
            if (!result.isSuccess()) {
                CompileFailure failure = new CompileFailure(kind, result.diagnostics(), Instant.now());
                throw new IllegalStateException(failure.describe());
            }
            LOG.info("Compiled {} {} source(s) in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
        }
    }

    /**
     * Compiles a batch's changes for the languages of a manifest, the application's or its tests' view, and
     * what depends on them: the changed sources of each language, then, until nothing new is produced, the
     * sources of every language that reference a class another one changed.
     *
     * @param compilers The compilers, by language
     * @param target The manifest whose roots, outputs and classpaths are compiled
     * @param joint The languages compiled jointly, by owner
     * @param changes The changed sources, by language
     * @param full Whether everything is compiled
     * @param seed The classes changed by an earlier compilation, whose dependents among these sources are compiled too
     * @return What was compiled, or the failure
     */
    static DevRuntime.CompileRound round(Map<SourceKind, SourceCompiler> compilers, DevManifest target, Map<SourceKind, SourceKind> joint, Map<SourceKind, SourceChanges> changes, boolean full, Set<String> seed) {
        Map<SourceKind, SourceChanges> sources = new EnumMap<>(SourceKind.class);
        changes.forEach((kind, change) -> sources.merge(joint.getOrDefault(kind, kind), change.settled(), SourceChanges::merge));
        // what another compilation changed reaches every language, as the changes of this batch do
        Set<SourceKind> kinds = full || !seed.isEmpty() ? compilers.keySet() : sources.keySet();
        boolean compiled = false;
        Set<SourceKind> compiledKinds = new LinkedHashSet<>();
        Set<String> affectedClasses = new LinkedHashSet<>();
        for (SourceKind kind : kinds) {
            SourceCompiler compiler = compilers.get(kind);
            if (compiler == null || joint.containsKey(kind) || !embedded(target, kind)) {
                continue;
            }
            SourceChanges change = sources.getOrDefault(kind, SourceChanges.NONE);
            CompilationRequest request = new CompilationRequest(kind, compilationRoots(target, kind, joint), change.changed(), change.deleted(),
                full || !target.isIncremental(), target.compileClasspath(), target.processorPath(),
                target.classOutput(kind), target.generatedSources(kind), compileOptions(target, kind, joint));
            if (!seed.isEmpty()) {
                request = request.withAffectedClasses(seed);
            }
            CompilationResult result = compiler.compile(request);
            if (!result.isSuccess()) {
                return DevRuntime.CompileRound.failed(new CompileFailure(kind, result.diagnostics(), Instant.now()));
            }
            compiled = true;
            compiledKinds.add(kind);
            affectedClasses.addAll(result.compiledClasses());
            LOG.info("Compiled {} {} source(s) in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
        }
        // a change in one language reaches the others: their sources that reference a changed class are
        // recompiled too, and fail rather than keep bytecode linked against what is gone
        // until nothing new is produced: a Groovy class recompiled for a Java change may itself be what a
        // Java source depends on, and Java was visited first
        Set<String> propagated = new LinkedHashSet<>();
        for (int pass = 0; pass < MAX_PROPAGATION_PASSES && compilers.size() - joint.size() > 1 && !affectedClasses.equals(propagated); pass++) {
            Set<String> fresh = new LinkedHashSet<>(affectedClasses);
            fresh.removeAll(propagated);
            propagated.addAll(affectedClasses);
            for (Map.Entry<SourceKind, SourceCompiler> entry : compilers.entrySet()) {
                SourceKind kind = entry.getKey();
                // a language compiled in this batch for its own changes is visited again: an unchanged source of
                // it that references what another language changed was not selected the first time
                if (joint.containsKey(kind) || !embedded(target, kind)) {
                    continue;
                }
                CompilationRequest request = new CompilationRequest(kind, compilationRoots(target, kind, joint), Set.of(), Set.of(), false, target.compileClasspath(),
                    target.processorPath(), target.classOutput(kind), target.generatedSources(kind), compileOptions(target, kind, joint)).withAffectedClasses(fresh);
                CompilationResult result = entry.getValue().compile(request);
                if (!result.isSuccess()) {
                    return DevRuntime.CompileRound.failed(new CompileFailure(kind, result.diagnostics(), Instant.now()));
                }
                if (result.status() == CompilationResult.Status.SUCCESS) {
                    compiledKinds.add(kind);
                    affectedClasses.addAll(result.compiledClasses());
                    LOG.info("Compiled {} {} source(s) that depend on the changed classes in {} ms", result.compiledSources().size(), kind, result.duration().toMillis());
                }
            }
        }
        return new DevRuntime.CompileRound(null, compiled, compiledKinds, affectedClasses);
    }
}
