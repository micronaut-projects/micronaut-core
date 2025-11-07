/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor;
import io.micronaut.annotation.processing.MixinVisitorProcessor;
import io.micronaut.annotation.processing.PackageElementVisitorProcessor;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.PythonAnnotationProcessor;

/**
 * Utility class for compiling Java sources with Micronaut annotation processors.
 *
 * @author Micronaut
 * @since 4.8.0
 */
final class PyronautJavaCompiler {

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private Consumer<ClassElement> classElementCallback;

    /**
     * Set the callback to be invoked for each class element created during processing.
     * This is primarily used for testing purposes.
     *
     * @param callback The callback function
     */
    public void setClassElementCallback(Consumer<ClassElement> callback) {
        this.classElementCallback = callback;
    }

    /**
     * Compile sources to memory using in-memory file manager.
     *
     * @param sources The Java sources to compile
     * @param classpath Additional classpath entries (null to use system classpath)
     * @return The compiled class files
     */
    Iterable<JavaFileObject> compileInMemory(JavaFileObject[] sources, List<File> classpath) {
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
            compiler.getStandardFileManager(diagnosticCollector, null, null));

        List<String> options = buildCompilerOptions(classpath);
        List<Processor> processors = getAnnotationProcessors();

        try {
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, // Writer for additional output
                fileManager, // File manager
                diagnosticCollector, // Diagnostic collector
                options, // Compiler options
                Collections.emptyList(), // Classes to process (none)
                Arrays.asList(sources) // Source files
            );

            task.setProcessors(processors);

            boolean success = task.call();
            if (!success) {
                throw new RuntimeException("Compilation failed: " + diagnosticCollector.getDiagnostics());
            }
        } finally {
            shutdownProcessors(processors);
        }

        return fileManager.getOutputFiles();
    }

    private static void shutdownProcessors(List<Processor> processors) {
        for (Processor processor : processors) {
            if (processor instanceof AutoCloseable autoCloseable) {
                try {
                    autoCloseable.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Compile sources to disk.
     *
     * @param targetDir The target directory for compiled classes
     * @param sources The Java sources to compile
     * @param classpath Additional classpath entries (null to use system classpath)
     */
    void compileToDisk(File targetDir, JavaFileObject[] sources, List<File> classpath) {
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticCollector, null, null);

        // Set the class output location
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(targetDir));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set output location", e);
        }

        List<String> options = buildCompilerOptions(classpath);
        List<Processor> processors = getAnnotationProcessors();

        try {
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, // Writer for additional output
                fileManager, // File manager
                diagnosticCollector, // Diagnostic collector
                options, // Compiler options
                Collections.emptyList(), // Classes to process (none)
                Arrays.asList(sources) // Source files
            );

            task.setProcessors(processors);

            boolean success = task.call();
            if (!success) {
                throw new RuntimeException("Compilation failed: " + diagnosticCollector.getDiagnostics());
            }
        } finally {
            shutdownProcessors(processors);
        }
    }

    private List<String> buildCompilerOptions(List<File> classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> options = new ArrayList<>();
        options.add("-classpath");
        StringBuilder cp = new StringBuilder();
        for (File file : classpath) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparator);
            }
            cp.append(file.getAbsolutePath());
        }
        options.add(cp.toString());
        return options;
    }

    /**
     * Get the list of Micronaut annotation processors to use.
     *
     * @return The processors
     */
    private List<Processor> getAnnotationProcessors() {
        List<Processor> processors = new ArrayList<>();
        processors.add(new MixinVisitorProcessor());
        processors.add(new PackageElementVisitorProcessor());
        processors.add(new TypeElementVisitorProcessor());
        processors.add(new AggregatingTypeElementVisitorProcessor());
        processors.add(new BeanDefinitionInjectProcessor());

        PythonAnnotationProcessor pythonProcessor = new PythonAnnotationProcessor();
        if (classElementCallback != null) {
            pythonProcessor.setClassElementCallback(classElementCallback);
        }
        // Enable testing mode to ensure proper cleanup of GraalVM contexts
        // This prevents memory leaks in test environments
        processors.add(pythonProcessor);

        return processors;
    }
}
