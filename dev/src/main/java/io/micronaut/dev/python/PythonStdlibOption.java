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
package io.micronaut.dev.python;

import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.dev.manifest.DevManifest;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Offers each generation of an application the {@link PythonStdlibBytecode compiled standard library} of Python, as a
 * context option at the lowest precedence: an application that configures {@code python.StdLibHome} keeps its own.
 * Lives in the parent tier, which keeps the state across generations, and loads nothing of GraalPy unless the Python
 * runtime is on the classpath.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class PythonStdlibOption {

    private static final String SOURCE = "micronaut-dev-python-stdlib";
    private static final String OPTION = "graalpy.context.options.python.StdLibHome";
    private static final String PYTHON_RUNTIME = "io.micronaut.context.python.GraalPyContextFactory";
    private static final Map<Path, PythonStdlibBytecode> TREES = new ConcurrentHashMap<>();

    private PythonStdlibOption() {
    }

    /**
     * The standard library for the generation about to start. In a native image the standard library comes with the
     * image, and this does nothing.
     *
     * @param manifest The manifest of the development runtime
     * @return The property source, or null to leave the contexts on the standard library GraalPy finds
     */
    public static @Nullable PropertySource propertySource(DevManifest manifest) {
        if (!manifest.pythonStdlibBytecode()
            || System.getProperty("org.graalvm.nativeimage.imagecode") != null
            || !ClassUtils.isPresent(PYTHON_RUNTIME, PythonStdlibOption.class.getClassLoader())) {
            return null;
        }
        Path directory = manifest.pythonStdlibBytecodeDirectory();
        Path root = directory != null ? directory : PythonStdlibBytecode.defaultDirectory();
        Path standardLibrary = TREES.computeIfAbsent(root, PythonStdlibBytecode::new).standardLibrary();
        return standardLibrary == null
            ? null
            : PropertySource.of(SOURCE, Map.<String, Object>of(OPTION, standardLibrary.toString()), Integer.MIN_VALUE);
    }
}
