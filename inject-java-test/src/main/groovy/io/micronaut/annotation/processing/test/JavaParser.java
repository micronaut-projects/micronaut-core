package io.micronaut.annotation.processing.test;

import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.util.Context;
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor;
import io.micronaut.annotation.processing.PackageConfigurationInjectProcessor;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;

import javax.annotation.Nonnull;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility for parsing Java code.
 *
 * @author graemerocher
 * @since 1.1
 */
public class JavaParser {

    /**
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
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
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        InMemoryJavaFileManager fileManager =
                new InMemoryJavaFileManager(
                        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
        Context context = new Context();
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
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     *
     * @param className The class name
     * @param code the raw code
     */
    public Iterable<? extends JavaFileObject> generate(String className, String code) {
        return generate(JavaFileObjects.forSourceString(className, code));
    }

    /**
     * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
     * <b>does not</b> compile the sources.
     *
     * @param sources The sources
     * @return The java file objects
     */
    public Iterable<? extends JavaFileObject> generate(JavaFileObject... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
        InMemoryJavaFileManager fileManager =
                new InMemoryJavaFileManager(
                        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
        Context context = new Context();
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
    protected @Nonnull List<Processor> getAnnotationProcessors() {
        List<Processor> processors = new ArrayList<>();
        processors.add(getTypeElementVisitorProcessor());
        processors.add(new PackageConfigurationInjectProcessor());
        processors.add(getBeanDefinitionInjectProcessor());
        return processors;
    }

    /**
     * The {@link BeanDefinitionInjectProcessor} to use.
     * @return The {@link BeanDefinitionInjectProcessor}
     */
    protected @Nonnull BeanDefinitionInjectProcessor getBeanDefinitionInjectProcessor() {
        return new BeanDefinitionInjectProcessor();
    }

    /**
     * The type element visitor processor to use.
     *
     * @return The type element visitor processor
     */
    protected @Nonnull TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
        return new TypeElementVisitorProcessor();
    }

}
