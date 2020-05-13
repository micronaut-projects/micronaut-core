/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test;

import com.google.testing.compile.JavaFileObjects;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor;
import io.micronaut.annotation.processing.PackageConfigurationInjectProcessor;
import io.micronaut.annotation.processing.ServiceDescriptionProcessor;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.tools.*;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility for parsing Java code.
 *
 * @author graemerocher
 * @since 1.1
 */
public class JavaParser implements Closeable {

    private final JavaCompiler compiler;
    private final InMemoryJavaFileManager fileManager;
    private final DiagnosticCollector<JavaFileObject> diagnosticCollector;
    private final Context context;

    /**
     * Default constructor.
     */
    public JavaParser() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        this.diagnosticCollector = new DiagnosticCollector<>();
        this.fileManager =
                new InMemoryJavaFileManager(
                        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
        this.context = new Context();
    }

    /**
     * Parses {@code sources} into {@linkplain com.sun.source.tree.CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     *
     * @param className The class name
     * @param lines The lines to parse
     * @return The elements
     */
    public Iterable<? extends Element> parseLines(String className, String... lines) {
        return parse(JavaFileObjects.forSourceLines(className.replace('.', File.separatorChar) + ".java", lines));
    }

    /**
     * Parses {@code sources} into {@code CompilationUnitTree} units. This method
     * <b>does not</b> compile the sources.
     *
     * @param sources The sources
     * @return The elements
     */
    public Iterable<? extends Element> parse(JavaFileObject... sources) {
        JavacTask task =
                ((JavacTool) compiler)
                        .getTask(
                                null, // explicitly use the default because old javac logs some output on stderr
                                fileManager,
                                diagnosticCollector,
                                Collections.emptySet(),
                                Collections.emptySet(),
                                Arrays.asList(sources),
                                context);
        try {
            task.parse();
            return task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                System.out.println(diagnostic);
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new RuntimeException(diagnostic.toString());
                }
            }
        }
    }

    /**
     * Parses {@code sources} into {@code CompilationUnitTree} units. This method
     * <b>does not</b> compile the sources.
     *
     * @param className The class name
     * @param code the raw code
     * @return The generated file objects
     */
    public Iterable<? extends JavaFileObject> generate(String className, String code) {
        return generate(JavaFileObjects.forSourceString(className, code));
    }

    /**
     * Reads the contents of a generated file as a reader.
     * @param filePath The file path
     * @param className The class name that produces the file
     * @param code The code of the class
     * @return The generated file
     * @throws IOException when an error occurs reading the file
     */
    public @Nullable Reader readGenerated(@NonNull String filePath, String className, String code) throws IOException {
        final String computedPath = fileManager.getMetaInfPath(filePath);
        final Iterable<? extends JavaFileObject> generatedFiles = generate(JavaFileObjects.forSourceString(className, code));
        for (JavaFileObject generatedFile : generatedFiles) {
            if (generatedFile.getName().equals(computedPath)) {
                return generatedFile.openReader(true);
            }
        }
        return null;
    }

    /**
     * Parses {@code sources} into {@code CompilationUnitTree} units. This method
     * <b>does not</b> compile the sources.
     *
     * @param sources The sources
     * @return The java file objects
     */
    public Iterable<? extends JavaFileObject> generate(JavaFileObject... sources) {

        JavacTask task =
                ((JavacTool) compiler)
                        .getTask(
                                null, // explicitly use the default because old javac logs some output on stderr
                                fileManager,
                                diagnosticCollector,
                                Collections.emptySet(),
                                Collections.emptySet(),
                                Arrays.asList(sources),
                                context);
        try {

            List<Processor> processors = getAnnotationProcessors();
            task.setProcessors(processors);
            task.generate();

            List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
            StringBuilder error = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    error.append(diagnostic);
                }
            }
            if (error.length() > 0) {
                throw new RuntimeException(error.toString());
            }
            return fileManager.getOutputFiles();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The list of processors to use.
     * @return The processor list
     */
    protected @NonNull List<Processor> getAnnotationProcessors() {
        List<Processor> processors = new ArrayList<>();
        processors.add(getTypeElementVisitorProcessor());
        processors.add(new PackageConfigurationInjectProcessor());
        processors.add(getBeanDefinitionInjectProcessor());
        processors.add(new ServiceDescriptionProcessor());
        return processors;
    }

    /**
     * The {@link BeanDefinitionInjectProcessor} to use.
     * @return The {@link BeanDefinitionInjectProcessor}
     */
    protected @NonNull BeanDefinitionInjectProcessor getBeanDefinitionInjectProcessor() {
        return new BeanDefinitionInjectProcessor();
    }

    /**
     * The type element visitor processor to use.
     *
     * @return The type element visitor processor
     */
    protected @NonNull TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
        return new TypeElementVisitorProcessor();
    }

    @Override
    public void close() {
        if (compiler != null) {
            try {
                ((com.sun.tools.javac.main.JavaCompiler) compiler).close();
            } catch (Exception e) {
                // ignore
            }
        }
        if (fileManager != null) {
            try {
                fileManager.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
