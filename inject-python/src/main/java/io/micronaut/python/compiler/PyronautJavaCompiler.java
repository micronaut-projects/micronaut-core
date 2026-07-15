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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.PythonAnnotationProcessor;
import org.jspecify.annotations.NonNull;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for compiling Java sources with Micronaut annotation processors.
 *
 * @author Micronaut
 * @since 5.2.0
 */
final class PyronautJavaCompiler {

    private static final String MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER = "micronaut.introspections.use.context.classloader";
    private static final Pattern SOURCE_IN_MESSAGE = Pattern.compile("Python source \\[([^]]+)]");
    private static final Pattern LINE_IN_MESSAGE = Pattern.compile("line (\\d+)");
    private static final DateTimeFormatter DUMP_FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final File DEFAULT_ERROR_DUMP_DIRECTORY = new File(
        System.getProperty("user.home"),
        ".pyronaut/processor-error-dumps"
    );

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private Consumer<ClassElement> classElementCallback;
    private boolean verboseErrors;
    private File errorDumpDirectory = DEFAULT_ERROR_DUMP_DIRECTORY;
    private List<SourceSnapshot> sourceSnapshots = List.of();

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
     * Set whether compiler failures should include full diagnostics in the thrown message.
     *
     * @param verboseErrors Whether verbose errors should be thrown
     */
    public void setVerboseErrors(boolean verboseErrors) {
        this.verboseErrors = verboseErrors;
    }

    /**
     * Set the directory for full compiler error dump files.
     *
     * @param errorDumpDirectory The dump directory
     */
    public void setErrorDumpDirectory(File errorDumpDirectory) {
        this.errorDumpDirectory = errorDumpDirectory;
    }

    /**
     * Set source snapshots used for concise snippets.
     *
     * @param sourceSnapshots The source snapshots
     */
    public void setSourceSnapshots(List<SourceSnapshot> sourceSnapshots) {
        this.sourceSnapshots = sourceSnapshots == null ? List.of() : List.copyOf(sourceSnapshots);
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
        List<File> processorClasspath = mergeClasspath(annotationProcessorPath, classpath);
        List<File> compileClasspath = effectiveClasspath(classpath);
        List<String> options = buildCompilerOptions(compileClasspath, bootclasspath, annotationProcessorPath, compilerOptions);
        ClassLoader classLoader = createAnnotationProcessorClassLoader(processorClasspath);
        System.setProperty(VisitorContext.MICRONAUT_PROCESSING_USE_CONTEXT_CLASSLOADER, StringUtils.TRUE);
        System.setProperty(MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER, StringUtils.TRUE);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);

        List<Processor> processors = getAnnotationProcessors(classLoader);

        boolean success;
        try {
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, // Writer for additional output
                fileManager, // File manager
                diagnosticCollector, // Diagnostic collector
                options, // Compiler options
                Collections.emptyList(), // Classes to process (none)
                Arrays.asList(sources) // Source files
            );

            if (!processors.isEmpty()) {
                task.setProcessors(processors);
            }

            success = task.call();
        } catch (RuntimeException e) {
            throw processingFailure(diagnosticCollector, e);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            System.clearProperty(VisitorContext.MICRONAUT_PROCESSING_USE_CONTEXT_CLASSLOADER);
            System.clearProperty(MICRONAUT_INTROSPECTIONS_USE_CONTEXT_CLASSLOADER);
            shutdownProcessors(processors);
        }
        if (!success) {
            throw processingFailure(diagnosticCollector, null);
        }
    }

    private RuntimeException processingFailure(DiagnosticCollector<JavaFileObject> diagnosticCollector, RuntimeException exception) {
        List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnostics(diagnosticCollector);
        String fullDetails = fullDetails(diagnostics, exception);
        DumpResult dumpResult = writeDump(fullDetails);
        String message = verboseErrors
            ? verboseMessage(fullDetails, dumpResult)
            : conciseMessage(diagnostics, exception, dumpResult);
        if (exception == null) {
            return new RuntimeException(message);
        }
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return new RuntimeException(message, cause);
    }

    private static List<Diagnostic<? extends JavaFileObject>> diagnostics(DiagnosticCollector<JavaFileObject> diagnosticCollector) {
        try {
            return new ArrayList<>(diagnosticCollector.getDiagnostics());
        } catch (ConcurrentModificationException e) {
            return List.of();
        }
    }

    private String conciseMessage(List<Diagnostic<? extends JavaFileObject>> diagnostics,
                                  RuntimeException exception,
                                  DumpResult dumpResult) {
        Diagnostic<? extends JavaFileObject> primary = primaryDiagnostic(diagnostics);
        String primaryMessage = primaryErrorMessage(primary, exception);
        StringBuilder message = new StringBuilder("Pyronaut processing failed: ")
            .append(primaryMessage);
        if (primary != null) {
            appendDiagnosticLocation(message, primary);
            appendSourceSnippet(message, primary);
        }
        appendPythonSnippet(message, primaryMessage);
        appendDumpResult(message, dumpResult);
        return message.toString();
    }

    private String verboseMessage(String fullDetails, DumpResult dumpResult) {
        StringBuilder message = new StringBuilder("Pyronaut processing failed with verbose diagnostics:")
            .append(System.lineSeparator())
            .append(System.lineSeparator())
            .append(fullDetails);
        appendDumpResult(message, dumpResult);
        return message.toString();
    }

    private static Diagnostic<? extends JavaFileObject> primaryDiagnostic(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                return diagnostic;
            }
        }
        return diagnostics.isEmpty() ? null : diagnostics.get(0);
    }

    private static String primaryErrorMessage(Diagnostic<? extends JavaFileObject> primary, RuntimeException exception) {
        if (primary != null) {
            String message = cleanMessage(primary.getMessage(Locale.getDefault()));
            if (!message.isBlank()) {
                return message;
            }
        }
        if (exception != null && exception.getMessage() != null) {
            return cleanMessage(exception.getMessage());
        }
        return "Processing failed";
    }

    private static String cleanMessage(String message) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : message.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("at ") || trimmed.startsWith("Failed Trace:")) {
                continue;
            }
            if (!cleaned.isEmpty()) {
                cleaned.append(System.lineSeparator());
            }
            cleaned.append(trimmed);
        }
        return cleaned.isEmpty() ? message.strip() : cleaned.toString();
    }

    private static void appendDiagnosticLocation(StringBuilder message, Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject source = diagnostic.getSource();
        if (source == null || diagnostic.getLineNumber() == Diagnostic.NOPOS) {
            return;
        }
        message.append(System.lineSeparator())
            .append("Location: ")
            .append(source.getName())
            .append(':')
            .append(diagnostic.getLineNumber());
        if (diagnostic.getColumnNumber() != Diagnostic.NOPOS) {
            message.append(':').append(diagnostic.getColumnNumber());
        }
    }

    private static void appendSourceSnippet(StringBuilder message, Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject source = diagnostic.getSource();
        if (source == null || diagnostic.getLineNumber() == Diagnostic.NOPOS) {
            return;
        }
        try {
            appendSnippet(message, source.getCharContent(true).toString(), (int) diagnostic.getLineNumber(), diagnostic.getColumnNumber(), "Java snippet");
        } catch (IOException ignored) {
            // Ignore unavailable source content in the concise message.
        }
    }

    private void appendPythonSnippet(StringBuilder message, String primaryMessage) {
        SourceSnapshot snapshot = findSourceSnapshot(primaryMessage);
        if (snapshot == null) {
            return;
        }
        int line = findLine(primaryMessage);
        message.append(System.lineSeparator())
            .append("Location: ")
            .append(snapshot.path())
            .append(':')
            .append(line)
            .append(System.lineSeparator())
            .append("Python source: ")
            .append(snapshot.path());
        appendSnippet(message, snapshot.content(), line, Diagnostic.NOPOS, "Python snippet");
    }

    private SourceSnapshot findSourceSnapshot(String message) {
        Matcher matcher = SOURCE_IN_MESSAGE.matcher(message);
        if (matcher.find()) {
            String sourceName = matcher.group(1);
            for (SourceSnapshot snapshot : sourceSnapshots) {
                if (snapshot.name().equals(sourceName) || snapshot.path().endsWith(sourceName)) {
                    return snapshot;
                }
            }
        }
        return sourceSnapshots.size() == 1 ? sourceSnapshots.get(0) : null;
    }

    private static int findLine(String message) {
        Matcher matcher = LINE_IN_MESSAGE.matcher(message);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }

    private static void appendSnippet(StringBuilder message, String source, int line, long column, String title) {
        List<String> lines = source.lines().toList();
        if (line < 1 || line > lines.size()) {
            return;
        }
        String snippet = lines.get(line - 1);
        message.append(System.lineSeparator())
            .append(title)
            .append(':')
            .append(System.lineSeparator())
            .append(snippet);
        if (column != Diagnostic.NOPOS && column > 0) {
            message.append(System.lineSeparator());
            for (int i = 1; i < column; i++) {
                message.append(' ');
            }
            message.append('^');
        }
    }

    private String fullDetails(List<Diagnostic<? extends JavaFileObject>> diagnostics, RuntimeException exception) {
        StringBuilder details = new StringBuilder();
        details.append("Diagnostics:");
        if (diagnostics.isEmpty()) {
            details.append(System.lineSeparator()).append("No javac diagnostics were reported.");
        } else {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                details.append(System.lineSeparator())
                    .append(formatDiagnostic(diagnostic));
            }
        }
        if (exception != null) {
            details.append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Exception:")
                .append(System.lineSeparator())
                .append(stackTrace(exception));
        }
        return details.toString();
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        StringBuilder formatted = new StringBuilder()
            .append(diagnostic.getKind())
            .append(": ");
        JavaFileObject source = diagnostic.getSource();
        if (source != null) {
            formatted.append(source.getName());
            if (diagnostic.getLineNumber() != Diagnostic.NOPOS) {
                formatted.append(':').append(diagnostic.getLineNumber());
                if (diagnostic.getColumnNumber() != Diagnostic.NOPOS) {
                    formatted.append(':').append(diagnostic.getColumnNumber());
                }
            }
            formatted.append(": ");
        }
        formatted.append(diagnostic.getMessage(Locale.getDefault()));
        return formatted.toString();
    }

    private DumpResult writeDump(String details) {
        try {
            Files.createDirectories(errorDumpDirectory.toPath());
            File dumpFile = new File(
                errorDumpDirectory,
                "pyronaut-compiler-error-" + DUMP_FILE_TIMESTAMP.format(LocalDateTime.now(ZoneId.systemDefault())) + ".log"
            );
            Files.writeString(dumpFile.toPath(), details);
            return DumpResult.written(dumpFile);
        } catch (IOException | RuntimeException e) {
            return DumpResult.failed(e);
        }
    }

    private static void appendDumpResult(StringBuilder message, DumpResult dumpResult) {
        message.append(System.lineSeparator());
        if (dumpResult.file() != null) {
            message.append("Full error details were written to: ")
                .append(dumpResult.file().getAbsolutePath());
        } else {
            message.append("Full error details could not be written: ")
                .append(dumpResult.failureMessage());
        }
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
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
        if (CollectionUtils.isNotEmpty(classpath)) {
            addClasspathOption("-classpath", classpath, options);
        }
        if (CollectionUtils.isNotEmpty(annotationProcessorPath)) {
            addClasspathOption("--processor-path", annotationProcessorPath, options);
        } else {
            // inherit from classpath
            options.add("-proc:full");
        }
        if (CollectionUtils.isNotEmpty(bootClasspath)) {
            addClasspathOption("-bootclasspath", bootClasspath, options);
        }
        if (compilerOptions != null) {
            options.addAll(compilerOptions);
        }
        return options;
    }

    private static List<File> effectiveClasspath(List<File> classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return classpath;
        }
        return mergeClasspath(defaultClasspath(), classpath);
    }

    private static List<File> defaultClasspath() {
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath == null || javaClassPath.isBlank()) {
            return List.of();
        }
        List<File> files = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(javaClassPath, File.pathSeparator);
        while (tokenizer.hasMoreTokens()) {
            String entry = tokenizer.nextToken();
            if (!entry.isBlank()) {
                files.add(new File(entry));
            }
        }
        return files;
    }

    private static List<File> mergeClasspath(List<File> first, List<File> second) {
        if (first == null || first.isEmpty()) {
            return second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        List<File> merged = new ArrayList<>(first);
        for (File file : second) {
            if (!merged.contains(file)) {
                merged.add(file);
            }
        }
        return merged;
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
    private @NonNull List<Processor> getAnnotationProcessors(ClassLoader classLoader) {
        List<Processor> processors = new ArrayList<>();
        processors.add(new MixinVisitorProcessor());
        processors.add(new PackageElementVisitorProcessor());
        processors.add(new TypeElementVisitorProcessor());
        processors.add(new AggregatingTypeElementVisitorProcessor());
        processors.add(new BeanDefinitionInjectProcessor());

        PythonAnnotationProcessor pythonProcessor = new PythonAnnotationProcessor();
        pythonProcessor.setClassLoader(classLoader);
        if (classElementCallback != null) {
            pythonProcessor.setClassElementCallback(classElementCallback);
        }
        // Enable testing mode to ensure proper cleanup of GraalVM contexts
        // This prevents memory leaks in test environments
        processors.add(pythonProcessor);

        return processors;
    }

    private static ClassLoader createAnnotationProcessorClassLoader(List<File> annotationProcessorPath) {
        ClassLoader classLoader = PythonAnnotationProcessor.class.getClassLoader();
        if (annotationProcessorPath != null) {
            List<URL> cp = annotationProcessorPath.stream().flatMap(f -> {
                try {
                    return Stream.of(f.toURI().toURL());
                } catch (MalformedURLException e) {
                    return Stream.empty();
                }
            }).toList();
            classLoader = new URLClassLoader(cp.toArray(new URL[0]), classLoader);
        }
        return classLoader;
    }

    record SourceSnapshot(String name, String path, String content) {
    }

    private record DumpResult(File file, String failureMessage) {
        static DumpResult written(File file) {
            return new DumpResult(file, null);
        }

        static DumpResult failed(Exception exception) {
            String message = exception.getMessage();
            return new DumpResult(null, message == null ? exception.getClass().getSimpleName() : message);
        }
    }
}
