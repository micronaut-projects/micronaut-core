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

import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor;
import io.micronaut.annotation.processing.MixinVisitorProcessor;
import io.micronaut.annotation.processing.PackageElementVisitorProcessor;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.PythonAnnotationProcessor;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for compiling Java sources with Micronaut annotation processors.
 *
 * @author Micronaut
 * @since 5.0.0
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
     * @param bootclasspath Boot classpath (null to use the default)
     * @param annotationProcessorPath Annotation processor path
     * @return The compiled class files
     */
    Iterable<JavaFileObject> compileInMemory(JavaFileObject[] sources, List<File> classpath,
                                             List<File> bootclasspath,
                                             List<File> annotationProcessorPath,
                                             List<String> compilerOptions) {
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        InMemoryJavaFileManager inMemoryJavaFileManager = new InMemoryJavaFileManager(
            compiler.getStandardFileManager(diagnosticCollector, null, null));

        compileJava(sources, classpath, bootclasspath, annotationProcessorPath, compilerOptions, inMemoryJavaFileManager, diagnosticCollector);

        return inMemoryJavaFileManager.getOutputFiles();
    }

    private void compileJava(JavaFileObject[] sources,
                             List<File> classpath,
                             List<File> bootclasspath,
                             List<File> annotationProcessorPath,
                             List<String> compilerOptions,
                             JavaFileManager fileManager,
                             DiagnosticCollector<JavaFileObject> diagnosticCollector) {
        List<String> options = buildCompilerOptions(classpath, bootclasspath, annotationProcessorPath, compilerOptions);
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
                throw new RuntimeException(
                    "Compilation failed: " + diagnosticCollector.getDiagnostics());
            }
        } finally {
            shutdownProcessors(processors);
        }
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
     * @param bootclasspath Boot classpath (null to use default)
     * @param annotationProcessorPath Annotation processor path
     */
    void compileToDisk(File targetDir,
                       JavaFileObject[] sources,
                       List<File> classpath,
                       List<File> bootclasspath,
                       List<File> annotationProcessorPath,
                       List<String> compilerOptions) {
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager =
            compiler.getStandardFileManager(diagnosticCollector, null, null);

        // Set the class output location
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
                Collections.singletonList(targetDir));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set output location", e);
        }

        compileJava(sources, classpath, bootclasspath, annotationProcessorPath, compilerOptions, fileManager, diagnosticCollector);
    }

    private List<String> buildCompilerOptions(List<File> classpath,
                                              List<File> bootClasspath,
                                              List<File> annotationProcessorPath,
                                              List<String> compilerOptions) {
        List<String> options = new ArrayList<>();
        addClasspathOption("-classpath", classpath, options);
        addClasspathOption("-bootclasspath", bootClasspath, options);
        addClasspathOption("-processorpath", annotationProcessorPath, options);
        if (compilerOptions != null) {
            options.addAll(compilerOptions);
        }
        return options;
    }

    private static void addClasspathOption(String option,
                                           List<File> classpath,
                                           List<String> options) {
        if (classpath == null || classpath.isEmpty()) {
            return;
        }
        options.add(option);
        var cp = new StringBuilder();
        for (File file : classpath) {
            if (!cp.isEmpty()) {
                cp.append(File.pathSeparator);
            }
            cp.append(file.getAbsolutePath());
        }
        var classpathString = cp.toString();
        options.add(classpathString);
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
