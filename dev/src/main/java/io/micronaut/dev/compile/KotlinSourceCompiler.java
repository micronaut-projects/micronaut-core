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
import io.micronaut.core.reflect.ClassUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The embedded Kotlin compiler: kotlinc through the Kotlin Build Tools API, preceded by KSP when the
 * project has symbol processors, run incrementally by {@link StagedSourceCompiler}. Available when
 * the Build Tools API and its implementation are on the launch classpath; the module does not depend
 * on them, and the build plugin puts them there at the project's Kotlin and KSP versions.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class KotlinSourceCompiler extends StagedSourceCompiler {

    private static final String TOOLCHAINS = "org.jetbrains.kotlin.buildtools.api.KotlinToolchains";

    @Override
    public Set<SourceKind> kinds() {
        return Set.of(SourceKind.KOTLIN);
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent(TOOLCHAINS, KotlinSourceCompiler.class.getClassLoader()) && KotlinCompilation.isAvailable();
    }

    @Override
    @Nullable
    protected Produced runCompilation(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("The Kotlin Build Tools API is not on the classpath: development mode compiles Kotlin sources with kotlin-build-tools-impl at the project's Kotlin version");
        }
        // the Kotlin types are referenced by that class alone, so this one loads without Kotlin present
        return KotlinCompilation.run(request, toCompile, hiddenClasses, staging, generatedStaging, diagnostics);
    }

    @Override
    protected boolean compilesEmptyBatches() {
        // a deletion reaches the symbol processors, which regenerate what depended on the deleted source
        return isAvailable() && KotlinCompilation.kspPresent();
    }

    /**
     * Where KSP keeps its outputs and caches between runs: beside the class output.
     *
     * @param request The request
     * @return The directory, which may not exist
     */
    static Path kspWork(CompilationRequest request) {
        return request.classOutput().resolveSibling(request.classOutput().getFileName() + "-ksp");
    }
}
