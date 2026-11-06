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

import io.micronaut.context.python.GraalPyContextCustomizer;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.manifest.DevManifest;
import org.graalvm.polyglot.Context;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Points the GraalPy contexts of a development runtime at the {@link PythonStdlibBytecode compiled standard library}.
 * Service-loaded for each context a generation builds, from the parent tier, which keeps the state across generations.
 * Does nothing outside development mode, or in a native image, whose standard library comes with the image.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class PythonStdlibCustomizer implements GraalPyContextCustomizer {

    private static final Map<Path, PythonStdlibBytecode> TREES = new ConcurrentHashMap<>();

    @Override
    public void customize(Context.Builder builder) {
        DevRuntime runtime = DevRuntime.current();
        if (runtime == null || System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            return;
        }
        DevManifest manifest = runtime.manifest();
        if (!manifest.pythonStdlibBytecode()) {
            return;
        }
        Path directory = manifest.pythonStdlibBytecodeDirectory();
        Path root = directory != null ? directory : PythonStdlibBytecode.defaultDirectory();
        Path standardLibrary = TREES.computeIfAbsent(root, PythonStdlibBytecode::new).standardLibrary();
        if (standardLibrary != null) {
            builder.option("python.StdLibHome", standardLibrary.toString());
        }
    }
}
