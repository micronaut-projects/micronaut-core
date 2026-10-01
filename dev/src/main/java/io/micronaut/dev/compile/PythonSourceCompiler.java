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

import java.util.Set;

/**
 * The embedded Python compiler: the Pyronaut compiler of {@code micronaut-inject-python}, which
 * generates the Java stubs and bean definitions of a Python module and compiles them with the
 * module's Java sources, in one javac run. It compiles the Java sources that share its class output
 * (see {@link #jointKinds()}), since the stubs and those sources reference each other.
 *
 * <p>The compiler keeps its own incremental state and output beside the class output, and a
 * compilation that succeeds replaces what changed in the class output as one transaction, so a
 * failed edit leaves the running generation intact. The GraalPy context that parses the sources is
 * kept warm between compilations. Available when {@code micronaut-inject-python} is on the launch
 * classpath; the module does not depend on it.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class PythonSourceCompiler implements SourceCompiler {

    private static final String COMPILER = "io.micronaut.python.compiler.PyronautCompiler";

    @Nullable
    private PythonCompilation compilation;

    @Override
    public Set<SourceKind> kinds() {
        return Set.of(SourceKind.PYTHON);
    }

    @Override
    public Set<SourceKind> jointKinds() {
        return Set.of(SourceKind.JAVA);
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent(COMPILER, PythonSourceCompiler.class.getClassLoader());
    }

    @Override
    public synchronized CompilationResult compile(CompilationRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("micronaut-inject-python is not on the classpath: development mode compiles Python sources with the Pyronaut compiler");
        }
        PythonCompilation current = compilation;
        if (current == null) {
            // the Python types are referenced by that class alone, so this one loads without them present
            current = new PythonCompilation();
            compilation = current;
        }
        return current.compile(request);
    }

    @Override
    public synchronized void close() {
        PythonCompilation current = compilation;
        compilation = null;
        if (current != null) {
            current.close();
        }
    }
}
