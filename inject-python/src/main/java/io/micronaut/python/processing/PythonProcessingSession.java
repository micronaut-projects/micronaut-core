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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

/**
 * Owns the initialized GraalPy processing context across multiple serialized compilations.
 *
 * <p>A session is not thread safe. Callers must serialize compilations that share it.</p>
 *
 * @since 5.2.0
 */
@Experimental
public final class PythonProcessingSession implements AutoCloseable {
    private PythonAstParser parser;
    private boolean closed;

    /**
     * Obtains the parser for a compilation, initializing GraalPy on first use.
     *
     * @param classLoader The annotation processor class loader
     * @param incremental Whether the compilation is incremental
     * @return The session parser
     */
    @Internal
    public PythonAstParser parser(ClassLoader classLoader, boolean incremental) {
        if (closed) {
            throw new IllegalStateException("Python processing session is closed");
        }
        if (parser == null) {
            parser = new PythonAstParser(classLoader, incremental);
        }
        return parser;
    }

    /**
     * Reports whether GraalPy has been initialized.
     *
     * @return Whether GraalPy has been initialized for this session
     */
    public boolean initialized() {
        return parser != null;
    }

    @Override
    public void close() {
        closed = true;
        if (parser != null) {
            parser.close();
            parser = null;
        }
    }
}
