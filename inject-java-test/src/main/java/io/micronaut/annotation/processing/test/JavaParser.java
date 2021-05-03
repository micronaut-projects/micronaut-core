/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing.test;

import com.sun.source.util.JavacTask;
import io.micronaut.annotation.processing.*;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import spock.util.environment.Jvm;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility for parsing Java code.
 * NOTE: Forked from Google Compile Testing Project
 *
 * @author graemerocher
 * @since 1.1
 */
public class JavaParser implements Closeable {

    private final JavaCompiler compiler;
    private final InMemoryJavaFileManager fileManager;
    private final DiagnosticCollector<JavaFileObject> diagnosticCollector;
    private JavacTask lastTask;

    /**
     * Default constructor.
     */
    public JavaParser() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        this.diagnosticCollector = new DiagnosticCollector<>();
        this.fileManager =
                new InMemoryJavaFileManager(
                        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    }

    /**
     * @return The compiler used
     */
    public JavaCompiler getCompiler() {
        return compiler;
    }

    /**
     * @return The file manager
     */
    public JavaFileManager getFileManager() {
        return fileManager;
    }

    /**
     * @return The filer
     */
    public Filer getFiler() {
        return fileManager;
    }

    /**
     * @return Dummy processing environment
     */
    public ProcessingEnvironment getProcessingEnv() {
        return new ProcessingEnvironment() {
            @Override
            public Map<String, String> getOptions() {
                return Collections.emptyMap();
            }

            @Override
            public Messager getMessager() {
                return new Messager() {
                    @Override
                    public void printMessage(Diagnostic.Kind kind, CharSequence msg) {

                    }

                    @Override
                    public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {

                    }

                    @Override
                    public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a) {

                    }

                    @Override
                    public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a, AnnotationValue v) {

                    }
                };
            }

            @Override
            public Filer getFiler() {
                return JavaParser.this.fileManager;
            }

            @Override
            public Elements getElementUtils() {
                if (lastTask == null) {
                    throw new IllegalStateException("Call parse first");
                }
                return lastTask.getElements();
            }

            @Override
            public Types getTypeUtils() {
                if (lastTask == null) {
                    throw new IllegalStateException("Call parse first");
                }
                return lastTask.getTypes();
            }

            @Override
            public SourceVersion getSourceVersion() {
                return SourceVersion.RELEASE_8;
            }

            @Override
            public Locale getLocale() {
                return Locale.getDefault();
            }
        };
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
        this.lastTask = getJavacTask(sources);
        try {
            lastTask.parse();
            return lastTask.analyze();
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
     * @return The last task that was used
     */
    public Optional<JavacTask> getLastTask() {
        return Optional.ofNullable(lastTask);
    }

    /**
     * gets the javac task.
     * @param sources The sources
     * @return the task
     */
    public JavacTask getJavacTask(JavaFileObject... sources) {
        Set<String> options = getCompilerOptions();
        this.lastTask = (JavacTask) compiler.getTask(
                null, // explicitly use the default because old javac logs some output on stderr
                fileManager,
                diagnosticCollector,
                options,
                Collections.emptySet(),
                Arrays.asList(sources)
        );
        return lastTask;
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
        JavacTask task = getJavacTask(sources);
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

    private Set<String> getCompilerOptions() {
        Set<String> options;
        if (Jvm.getCurrent().isJava15Compatible()) {
            options = CollectionUtils.setOf(
                    "--enable-preview",
                    "-source",
                    Jvm.getCurrent().getJavaSpecificationVersion()
            );
        } else {
            options = Collections.emptySet();
        }
        return options;
    }

    /**
     * The list of processors to use.
     * @return The processor list
     */
    protected @NonNull List<Processor> getAnnotationProcessors() {
        List<Processor> processors = new ArrayList<>();
        processors.add(getTypeElementVisitorProcessor());
        processors.add(getAggregatingTypeElementVisitorProcessor());
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

    /**
     * The type element visitor processor to use.
     *
     * @return The type element visitor processor
     */
    protected @NonNull AggregatingTypeElementVisitorProcessor getAggregatingTypeElementVisitorProcessor() {
        return new AggregatingTypeElementVisitorProcessor();
    }

    @Override
    public void close() {
        if (compiler != null) {
            try {
                // avoid illegal access
                if (!Jvm.getCurrent().isJava15Compatible()) {
                    ((com.sun.tools.javac.main.JavaCompiler) compiler).close();
                }
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
