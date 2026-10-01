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

import java.io.Closeable;
import java.util.Set;

/**
 * Compiles the sources of one or more languages into the class output directory, inside the
 * development JVM.
 *
 * <p>Implementations are discovered through {@code META-INF/services} and asked whether they are
 * {@link #isAvailable() available}: the Kotlin and Groovy compilers are only when the project put
 * the compiler on the launch classpath. A compiler keeps whatever warm state it can between
 * requests (loaded processors, a compiler session) and releases it when closed.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface SourceCompiler extends Closeable {

    /**
     * @return The languages this compiler handles
     */
    Set<SourceKind> kinds();

    /**
     * Whether the compiler can run in this JVM.
     *
     * @return True if its implementation is present
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Compiles the request.
     *
     * <p>A failed compilation leaves the output directory as it was, so the running application
     * keeps serving the previous generation. The result's diagnostics say what went wrong.</p>
     *
     * @param request What to compile
     * @return The result
     */
    CompilationResult compile(CompilationRequest request);

    @Override
    default void close() {
        // nothing to release by default
    }
}
