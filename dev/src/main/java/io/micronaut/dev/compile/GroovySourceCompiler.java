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
 * The embedded Groovy compiler: groovyc through {@code CompilationUnit}, with the Micronaut AST
 * transformations the compile classpath carries, run incrementally by {@link StagedSourceCompiler}.
 * Available when Groovy is on the launch classpath; the module does not depend on it.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class GroovySourceCompiler extends StagedSourceCompiler {

    private static final String COMPILATION_UNIT = "org.codehaus.groovy.control.CompilationUnit";

    @Override
    public Set<SourceKind> kinds() {
        return Set.of(SourceKind.GROOVY);
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent(COMPILATION_UNIT, GroovySourceCompiler.class.getClassLoader());
    }

    @Override
    @Nullable
    protected Produced runCompilation(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("Groovy is not on the classpath: development mode compiles Groovy sources with the project's own Groovy");
        }
        // the Groovy types are referenced by that class alone, so this one loads without Groovy present
        return GroovyCompilation.run(request, toCompile, hiddenClasses, staging, diagnostics);
    }
}
