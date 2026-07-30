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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.PythonSourceVisitor;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Processor;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Compiler for Python applications using Micronaut annotation processing.
 * This compiler can operate in two modes:
 * <ul>
 *   <li>In-memory mode: {@link #buildClassLoader()} - compiles to memory and returns a ClassLoader</li>
 *   <li>File system mode: {@link #compile()} - compiles to disk when targetDir is specified</li>
 * </ul>
 *
 * @author Micronaut
 * @since 5.2.0
 */
@Experimental
public final class PyronautCompiler {

    private static final Pattern JAVA_PACKAGE_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9_]*(\\.[a-z][a-zA-Z0-9_]*)*$");
    private static final String DEFAULT_PACKAGE_NAME = "pyronaut_application";

    private final String packageName;
    private final String pythonSrc;
    private final String pythonCode;
    private final String javaSrc;
    private final String applicationClass;
    private final File targetDir;
    private final List<File> annotationProcessorPath;
    private final List<File> classpath;
    private final List<File> runtimeClasspath;
    private final List<File> bootclasspath;
    private final ClassLoader parentClassLoader;
    private final Consumer<ClassElement> classElementCallback;
    private final List<String> compilerOptions;
    private final boolean verboseErrors;
    private final boolean compilePythonBytecode;
    private final File errorDumpDirectory;
    private final List<PythonSourceVisitor> pythonSourceVisitors;
    private final List<Processor> annotationProcessors;
    private final boolean incremental;
    private final File incrementalCacheDirectory;
    private final PythonIncrementalMode pythonIncrementalMode;

    private PyronautCompiler(Builder builder) {
        this.packageName = builder.packageName;
        this.pythonSrc = builder.pythonSrc;
        this.pythonCode = builder.pythonCode;
        this.javaSrc = builder.javaSrc;
        this.applicationClass = builder.applicationClass;
        this.targetDir = builder.targetDir;
        this.annotationProcessorPath = builder.annotationProcessorPath != null ? List.copyOf(builder.annotationProcessorPath) : null;
        this.bootclasspath = builder.bootclasspath != null ? List.copyOf(builder.bootclasspath) : null;
        this.classpath = builder.classpath != null ? List.copyOf(builder.classpath) : null;
        this.runtimeClasspath = builder.runtimeClasspath != null ? List.copyOf(builder.runtimeClasspath) : null;
        this.parentClassLoader = builder.parentClassLoader != null ? builder.parentClassLoader : PyronautCompiler.class.getClassLoader();
        this.classElementCallback = builder.classElementCallback;
        this.compilerOptions = builder.compilerOptions != null ? List.copyOf(builder.compilerOptions) : null;
        this.verboseErrors = builder.verboseErrors;
        this.compilePythonBytecode = builder.compilePythonBytecode;
        this.errorDumpDirectory = builder.errorDumpDirectory;
        this.annotationProcessors = builder.annotationProcessors == null ? List.of() : List.copyOf(builder.annotationProcessors);
        this.pythonSourceVisitors = builder.pythonSourceVisitors == null ? List.of() : List.copyOf(builder.pythonSourceVisitors);
        this.incremental = builder.incremental;
        this.incrementalCacheDirectory = builder.incrementalCacheDirectory;
        this.pythonIncrementalMode = builder.pythonIncrementalMode;
        validateConfiguration();
    }

    public static void main(String[] args) {
        String sourceDir = args[0];
        String destinationDir = args[1];

        PyronautCompiler.builder()
            .pythonSrc(sourceDir)
            .targetDir(new File(destinationDir))
            .build()
            .compile();
    }

    /**
     * Create a new builder for PyronautCompiler.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Compile the application in-memory and return a ClassLoader containing the compiled classes.
     * This method is used when you want to load and run the application without writing to disk.
     *
     * @return A ClassLoader containing the compiled application classes
     * @throws IllegalStateException if processing fails
     */
    public ClassLoader buildClassLoader() {
        PyronautJavaCompiler compiler = createCompiler();
        if (classElementCallback != null) {
            compiler.setClassElementCallback(classElementCallback);
        }
        JavaFileObject[] sources = createJavaSources();
        Iterable<JavaFileObject> compiledClasses = compiler.compileInMemory(sources, classpath, bootclasspath, annotationProcessorPath, compilerOptions);
        return new JavaFileObjectClassLoader(compiledClasses, createRuntimeClassLoader());
    }

    private ClassLoader createRuntimeClassLoader() {
        List<File> effectiveRuntimeClasspath = runtimeClasspath != null ? runtimeClasspath : classpath;
        if (effectiveRuntimeClasspath == null || effectiveRuntimeClasspath.isEmpty()) {
            return parentClassLoader;
        }
        URL[] urls = effectiveRuntimeClasspath.stream()
            .map(PyronautCompiler::toUrl)
            .toArray(URL[]::new);
        return new URLClassLoader(urls, parentClassLoader);
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid classpath entry: " + file, e);
        }
    }

    /**
     * Compile the application to the file system.
     * Requires that targetDir was specified in the builder.
     *
     * @throws IllegalStateException if targetDir is not set or processing fails
     */
    public void compile() {
        if (targetDir == null) {
            throw new IllegalStateException("targetDir must be specified for file system processing mode");
        }

        PyronautJavaCompiler compiler = createCompiler();
        if (!incremental) {
            JavaFileObject[] sources = createJavaSources();
            compiler.compileToDisk(targetDir, sources, classpath, bootclasspath, annotationProcessorPath, compilerOptions);
            return;
        }

        File cacheDirectory = incrementalCacheDirectory != null
            ? incrementalCacheDirectory
            : defaultIncrementalCacheDirectory();
        List<String> incrementalCompilerOptions = incrementalCompilerOptions();
        IncrementalCompilation incrementalCompilation = new IncrementalCompilation(
            javaSrc,
            pythonSrc,
            pythonCode,
            getPackageName(),
            applicationClass,
            targetDir,
            cacheDirectory,
            classpath,
            bootclasspath,
            annotationProcessorPath,
            incrementalCompilerOptions,
            compilePythonBytecode,
            annotationProcessors,
            pythonSourceVisitors,
            pythonIncrementalMode
        );
        Set<String> aggregatingSources = compiler.aggregatingSources(
            classpath,
            annotationProcessorPath,
            javaSrc,
            pythonSrc
        );
        IncrementalCompilation.Plan plan = incrementalCompilation.plan(aggregatingSources);
        if (plan.upToDate()) {
            return;
        }
        try {
            IncrementalCompilationTrace compilationTrace = compileIncrementally(
                compiler,
                incrementalCompilation,
                plan,
                incrementalCompilerOptions
            );
            if (!plan.fullRebuild()
                && (!compilationTrace.processorCompatible()
                    || !compilationTrace.contractViolatingOutputs().isEmpty())) {
                incrementalCompilation.invalidate();
                if (!annotationProcessors.isEmpty()) {
                    incrementalCompilation.prepareOutput(incrementalCompilation.plan(aggregatingSources));
                    throw new PyronautCompilerException(
                        "An explicitly supplied annotation processor violated its incremental "
                            + "contract; the output was cleaned and must be compiled again with "
                            + "fresh processor instances"
                    );
                }
                plan = incrementalCompilation.plan(aggregatingSources);
                compilationTrace = compileIncrementally(
                    compiler,
                    incrementalCompilation,
                    plan,
                    incrementalCompilerOptions
                );
            }
            incrementalCompilation.complete(plan, compilationTrace);
        } catch (RuntimeException e) {
            incrementalCompilation.invalidate();
            throw e;
        }
    }

    private IncrementalCompilationTrace compileIncrementally(
        PyronautJavaCompiler compiler,
        IncrementalCompilation incrementalCompilation,
        IncrementalCompilation.Plan plan,
        List<String> incrementalCompilerOptions
    ) {
        incrementalCompilation.prepareOutput(plan);
        compiler.setProcessAggregatingPythonVisitors(plan.fullRebuild() || plan.aggregating());
        boolean includeGeneratedApplication = applicationClass == null
            && (plan.fullRebuild()
                || plan.affectsPythonSources()
                || plan.aggregatingAffectsPythonSources());
        if (plan.fullRebuild() || !plan.processesAllPythonSources()) {
            compiler.setIncrementalPythonSources(
                plan.fullRebuild() ? null : plan.pythonSources()
            );
        }
        JavaFileObject[] sources = createJavaSources(plan.javaSources(), includeGeneratedApplication);
        if (sources.length == 0) {
            return IncrementalCompilationTrace.empty();
        }
        List<File> effectiveClasspath = plan.fullRebuild()
            ? classpath
            : appendClasspath(classpath, targetDir);
        return compiler.compileToDisk(
            targetDir,
            sources,
            effectiveClasspath,
            bootclasspath,
            annotationProcessorPath,
            incrementalCompilerOptions
        );
    }

    private File defaultIncrementalCacheDirectory() {
        File absoluteTarget = targetDir.getAbsoluteFile();
        File parent = absoluteTarget.getParentFile();
        if (parent == null) {
            parent = new File(".").getAbsoluteFile();
        }
        return new File(parent, "." + absoluteTarget.getName() + "-incremental");
    }

    private static List<File> appendClasspath(List<File> classpath, File entry) {
        Set<File> result = new LinkedHashSet<>();
        if (classpath != null) {
            result.addAll(classpath);
        }
        result.add(entry);
        return List.copyOf(result);
    }

    private List<String> incrementalCompilerOptions() {
        List<String> options = new ArrayList<>();
        if (compilerOptions != null) {
            compilerOptions.stream()
                .filter(option -> !option.startsWith("-Amicronaut.processing.incremental="))
                .forEach(options::add);
        }
        options.add("-Amicronaut.processing.incremental=true");
        return List.copyOf(options);
    }

    private PyronautJavaCompiler createCompiler() {
        PyronautJavaCompiler compiler = new PyronautJavaCompiler();
        compiler.setVerboseErrors(verboseErrors);
        if (errorDumpDirectory != null) {
            compiler.setErrorDumpDirectory(errorDumpDirectory);
        }
        compiler.setSourceSnapshots(createSourceSnapshots());
        compiler.setCompilePythonBytecode(compilePythonBytecode);
        compiler.setAnnotationProcessors(annotationProcessors);
        compiler.setPythonSourceVisitors(pythonSourceVisitors);
        return compiler;
    }

    private void validateConfiguration() {
        // Validate package name
        if (packageName != null && !JAVA_PACKAGE_PATTERN.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }

        // Validate that at least one source language is specified
        if ((pythonSrc == null || pythonSrc.isEmpty())
            && (pythonCode == null || pythonCode.isEmpty())
            && (javaSrc == null || javaSrc.isEmpty())) {
            throw new IllegalArgumentException("At least one Python or Java source must be specified");
        }
    }

    private JavaFileObject[] createJavaSources() {
        return createJavaSources(null, true);
    }

    private JavaFileObject[] createJavaSources(Set<Path> selectedSources,
                                               boolean includeGeneratedApplication) {
        List<JavaFileObject> sources = new ArrayList<>();

        // Add user-provided Java sources if specified
        if (javaSrc != null && !javaSrc.isEmpty()) {
            try {
                addJavaSourcesFromDirectory(sources, Paths.get(javaSrc), selectedSources);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read Java sources from: " + javaSrc, e);
            }
        }

        if (applicationClass != null && !applicationClass.isEmpty()) {
            // User provided application class - check if it exists in javaSrc
            if (javaSrc == null || javaSrc.isEmpty()) {
                throw new IllegalArgumentException("javaSrc must be specified when applicationClass is provided");
            }
            // The application class should already be in the sources list from javaSrc
        } else if (includeGeneratedApplication) {
            // Generate the default PyronautMain class
            String className = getPackageName() + ".PyronautMain";
            String sourceCode = generateMainClassSource();
            sources.add(new SimpleJavaFileObject(
                java.net.URI.create("string:///" + className.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE
            ) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return sourceCode;
                }
            });
        }

        return sources.toArray(new JavaFileObject[0]);
    }

    private List<PyronautJavaCompiler.SourceSnapshot> createSourceSnapshots() {
        List<PyronautJavaCompiler.SourceSnapshot> snapshots = new ArrayList<>();
        if (pythonCode != null && !pythonCode.isEmpty()) {
            snapshots.add(new PyronautJavaCompiler.SourceSnapshot("python-code", "inline python code", pythonCode));
        }
        if (pythonSrc != null && !pythonSrc.isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(pythonSrc, ",");
            while (tokenizer.hasMoreTokens()) {
                Path dir = Paths.get(tokenizer.nextToken().trim());
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> paths = Files.walk(dir)) {
                    paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".py"))
                        .forEach(path -> addSourceSnapshot(snapshots, path));
                } catch (IOException ignored) {
                    // Source reading failures will be reported by the annotation processor.
                }
            }
        }
        return snapshots;
    }

    private static void addSourceSnapshot(List<PyronautJavaCompiler.SourceSnapshot> snapshots, Path path) {
        try {
            snapshots.add(new PyronautJavaCompiler.SourceSnapshot(path.getFileName().toString(), path.toString(), Files.readString(path)));
        } catch (IOException ignored) {
            // Source reading failures will be reported by the annotation processor.
        }
    }

    private void addJavaSourcesFromDirectory(List<JavaFileObject> sources,
                                             Path dir,
                                             Set<Path> selectedSources) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .filter(path -> selectedSources == null
                     || selectedSources.contains(normalizeSourcePath(path)))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path);
                         sources.add(new SimpleJavaFileObject(
                             path.toAbsolutePath().normalize().toUri(),
                             JavaFileObject.Kind.SOURCE
                         ) {
                             @Override
                             public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                                 return content;
                             }
                         });
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to read Java source: " + path, e);
                     }
                 });
        }
    }

    private static Path normalizeSourcePath(Path path) {
        try {
            return path.toRealPath().normalize();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private String generateMainClassSource() {
        boolean hasSrc = pythonSrc != null && !pythonSrc.isEmpty();
        boolean hasCode = pythonCode != null && !pythonCode.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(getPackageName()).append(";\n\n");
        sb.append("import io.micronaut.runtime.Micronaut;\n");
        if (!hasSrc && !hasCode) {
            sb.append("\nclass PyronautMain {\n");
            sb.append("    public static void main(String[] args) {\n");
            sb.append("        Micronaut.run(args);\n");
            sb.append("    }\n");
            sb.append("}\n");
            return sb.toString();
        }

        sb.append("import io.micronaut.context.python.annotation.PythonApplication;\n\n");
        sb.append("@PythonApplication(\n");

        if (hasSrc) {
            sb.append("    src = \"").append(pythonSrc).append("\"");
            if (hasCode) {
                sb.append(",\n");
            }
        }
        if (hasCode) {
            sb.append("    code = \"").append(escapeJavaString(pythonCode)).append("\"");
        }

        sb.append("\n)\n");
        sb.append("class PyronautMain {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        Micronaut.run(args);\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String getPackageName() {
        return DEFAULT_PACKAGE_NAME;
    }

    private String escapeJavaString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Builder for PyronautCompiler.
     */
    @Experimental
    public static final class Builder {
        private String packageName;
        private String pythonSrc;
        private String pythonCode;
        private String javaSrc;
        private String applicationClass;
        private File targetDir;
        private List<File> annotationProcessorPath;
        private List<File> bootclasspath;
        private List<File> classpath;
        private List<File> runtimeClasspath;
        private ClassLoader parentClassLoader;
        private List<String> compilerOptions;
        private Consumer<ClassElement> classElementCallback;
        private boolean verboseErrors;
        private boolean compilePythonBytecode;
        private File errorDumpDirectory;
        private List<PythonSourceVisitor> pythonSourceVisitors;
        private List<Processor> annotationProcessors;
        private boolean incremental;
        private File incrementalCacheDirectory;
        private PythonIncrementalMode pythonIncrementalMode = PythonIncrementalMode.CONSERVATIVE;

        private Builder() {
        }

        /**
         * Set the package name for the generated application class.
         * Must be a valid Java package name. Defaults to "pyronaut_application".
         *
         * @param packageName The package name
         * @return This builder
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Set the Python source directory to scan for .py files.
         *
         * @param pythonSrc The Python source directory path
         * @return This builder
         */
        public Builder pythonSrc(String pythonSrc) {
            this.pythonSrc = pythonSrc;
            return this;
        }

        /**
         * Set the inline Python code to process.
         *
         * @param pythonCode The Python code
         * @return This builder
         */
        public Builder pythonCode(String pythonCode) {
            this.pythonCode = pythonCode;
            return this;
        }

        /**
         * Set the Java source directory to scan for .java files to include in processing.
         *
         * @param javaSrc The Java source directory path
         * @return This builder
         */
        public Builder javaSrc(String javaSrc) {
            this.javaSrc = javaSrc;
            return this;
        }

        /**
         * Set the fully qualified name of an existing application class annotated with @PythonApplication.
         * If specified, no Java source will be generated. Requires javaSrc to be set.
         *
         * @param applicationClass The application class name
         * @return This builder
         */
        public Builder applicationClass(String applicationClass) {
            this.applicationClass = applicationClass;
            return this;
        }

        /**
         * Set the target directory for file system processing mode.
         *
         * @param targetDir The target directory
         * @return This builder
         */
        public Builder targetDir(File targetDir) {
            this.targetDir = targetDir;
            return this;
        }

        /**
         * Set annotation processor path entries for processing.
         *
         * @param annotationProcessorPath The classpath files
         * @return This builder
         */
        public Builder annotationProcessorPath(List<File> annotationProcessorPath) {
            this.annotationProcessorPath = annotationProcessorPath;
            return this;
        }

        /**
         * Set additional classpath entries for processing.
         *
         * @param classpath The classpath files
         * @return This builder
         */
        public Builder classpath(List<File> classpath) {
            this.classpath = classpath;
            return this;
        }

        /**
         * Set classpath entries used by the in-memory application runtime.
         * When unset, the compile classpath is used for backward compatibility.
         *
         * @param runtimeClasspath The runtime classpath files
         * @return This builder
         */
        public Builder runtimeClasspath(List<File> runtimeClasspath) {
            this.runtimeClasspath = runtimeClasspath;
            return this;
        }

        /**
         * Set boot classpath entries.
         *
         * @param bootclasspath The boot classpath files
         * @return This builder
         */
        public Builder bootclasspath(List<File> bootclasspath) {
            this.bootclasspath = bootclasspath;
            return this;
        }

        /**
         * Set the parent classloader for in-memory processing results.
         * This lets generated application classes resolve user runtime dependencies
         * without requiring generated classes to be written to disk first.
         *
         * @param parentClassLoader The parent classloader
         * @return This builder
         */
        public Builder parentClassLoader(ClassLoader parentClassLoader) {
            this.parentClassLoader = parentClassLoader;
            return this;
        }

        /**
         * Set a callback to be invoked for each class element created during processing.
         * This is primarily used for testing purposes to capture class elements.
         *
         * @param classElementCallback The callback function
         * @return This builder
         */
        public Builder classElementCallback(Consumer<ClassElement> classElementCallback) {
            this.classElementCallback = classElementCallback;
            return this;
        }

        /**
         * Sets compiler options.
         * @param compilerOptions the compiler options
         * @return this builder
         */
        public Builder options(List<String> compilerOptions) {
            this.compilerOptions = compilerOptions;
            return this;
        }

        /**
         * Include full diagnostics and stack traces in thrown compiler errors.
         *
         * @param verboseErrors Whether verbose errors should be thrown
         * @return this builder
         */
        public Builder verboseErrors(boolean verboseErrors) {
            this.verboseErrors = verboseErrors;
            return this;
        }

        /**
         * Enable generation of hash-based GraalPy bytecode for Python resources generated during
         * annotation processing. Source resources remain available alongside the bytecode.
         *
         * @param compilePythonBytecode Whether generated Python resources should include bytecode
         * @return This builder
         * @since 5.2.0
         */
        public Builder compilePythonBytecode(boolean compilePythonBytecode) {
            this.compilePythonBytecode = compilePythonBytecode;
            return this;
        }

        /**
         * Enable incremental disk compilation. Incremental compilation is disabled by default and
         * has no effect on {@link PyronautCompiler#buildClassLoader()}.
         *
         * @param incremental Whether disk compilation should reuse compatible previous outputs
         * @return This builder
         * @since 5.2.0
         */
        public Builder incremental(boolean incremental) {
            this.incremental = incremental;
            return this;
        }

        /**
         * Set the directory used to persist incremental compilation state. The directory must not
         * contain, or be contained by, the target output directory.
         *
         * @param incrementalCacheDirectory The incremental compilation state directory
         * @return This builder
         * @since 5.2.0
         */
        public Builder incrementalCacheDirectory(File incrementalCacheDirectory) {
            this.incrementalCacheDirectory = incrementalCacheDirectory;
            return this;
        }

        /**
         * Set how incremental compilation handles dynamic or unresolved Python relationships.
         * Defaults to {@link PythonIncrementalMode#CONSERVATIVE}.
         *
         * @param pythonIncrementalMode The Python incremental dependency policy
         * @return This builder
         * @since 5.2.0
         */
        public Builder pythonIncrementalMode(PythonIncrementalMode pythonIncrementalMode) {
            this.pythonIncrementalMode = java.util.Objects.requireNonNull(
                pythonIncrementalMode,
                "pythonIncrementalMode"
            );
            return this;
        }

        /**
         * Set the directory used for full compiler error dump files.
         *
         * @param errorDumpDirectory The dump directory
         * @return this builder
         */
        public Builder errorDumpDirectory(File errorDumpDirectory) {
            this.errorDumpDirectory = errorDumpDirectory;
            return this;
        }

        /**
         * Supplies visitors for Python source metadata.
         *
         * @param pythonSourceVisitors visitors to invoke for Python source metadata in this compilation
         * @return this builder
         */
        public Builder pythonSourceVisitors(List<? extends PythonSourceVisitor> pythonSourceVisitors) {
            this.pythonSourceVisitors = List.copyOf(pythonSourceVisitors);
            return this;
        }

        /**
         * Supplies annotation processors used only for this compilation.
         *
         * @param annotationProcessors processors to invoke before standard Pyronaut processors
         * @return this builder
         */
        public Builder annotationProcessors(List<? extends Processor> annotationProcessors) {
            this.annotationProcessors = List.copyOf(annotationProcessors);
            return this;
        }

        /**
         * Build the PyronautCompiler instance.
         *
         * @return A new PyronautCompiler
         */
        public PyronautCompiler build() {
            return new PyronautCompiler(this);
        }
    }
}
