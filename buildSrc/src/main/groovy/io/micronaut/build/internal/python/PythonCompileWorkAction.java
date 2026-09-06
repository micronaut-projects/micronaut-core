/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.internal.python;

import org.gradle.api.GradleException;
import org.gradle.workers.WorkAction;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the Pyronaut compiler inside a Gradle worker daemon.
 * <p>
 * Gradle reuses a process-isolated worker for every submission with the same classpath and fork
 * options within one build (the public Worker API keeps workers for the session only), so a build
 * with several Python compile tasks pays the JVM start once. Each submission still builds its own
 * compiler and GraalPy engines; nothing is shared between compilations.
 */
public abstract class PythonCompileWorkAction implements WorkAction<PythonCompileParameters> {

    private static final String PYRONAUT_COMPILER_MAIN_CLASS = "io.micronaut.python.compiler.PyronautCompiler";

    @Override
    public void execute() {
        String destinationDir = getParameters().getDestinationDir().get();
        List<File> classpath = new ArrayList<>(getParameters().getClasspath().getFiles());
        // one compiler run over every root: each run writes the launcher, the VFS file list and the
        // package initialisers for the whole output, so a run per root would keep only the last root's
        compile(String.join(",", getParameters().getSourceDirs().get()), destinationDir, classpath);
    }

    private void compile(String sourceDirs, String destinationDir, List<File> classpath) {
        try {
            Class<?> compiler = Class.forName(PYRONAUT_COMPILER_MAIN_CLASS, true, getClass().getClassLoader());
            Object builder = compiler.getMethod("builder").invoke(null);
            Class<?> builderType = builder.getClass();
            builderType.getMethod("pythonSrc", String.class).invoke(builder, sourceDirs);
            builderType.getMethod("targetDir", File.class).invoke(builder, new File(destinationDir));
            builderType.getMethod("classpath", List.class).invoke(builder, classpath);
            builderType.getMethod("annotationProcessorPath", List.class).invoke(builder, classpath);
            Object instance = builderType.getMethod("build").invoke(builder);
            instance.getClass().getMethod("compile").invoke(instance);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new GradleException("Python compilation failed for source directories [" + sourceDirs + "]: " + cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("The Pyronaut compiler is not on the worker classpath: " + e.getMessage(), e);
        }
    }
}
