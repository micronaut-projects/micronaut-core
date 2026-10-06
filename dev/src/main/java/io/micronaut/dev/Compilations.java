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
import io.micronaut.dev.compile.SourceCompiler;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.manifest.DevManifest;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a compilation of a language of a {@link DevRuntime}'s manifest reads: its roots and options, with those of the
 * languages it compiles jointly, and which languages the runtime compiles at all.
 */
@Internal
@NullMarked
final class Compilations {

    // the runtime's logger: what is logged here is logged as the runtime's
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);

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
}
