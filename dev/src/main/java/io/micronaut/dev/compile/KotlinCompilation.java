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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Internal;
import org.jetbrains.kotlin.buildtools.api.BaseCompilationOperation;
import org.jetbrains.kotlin.buildtools.api.CompilationResult;
import org.jetbrains.kotlin.buildtools.api.KotlinLogger;
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains;
import org.jetbrains.kotlin.buildtools.api.NoImplementationFoundException;
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain;
import org.jetbrains.kotlin.buildtools.api.jvm.operations.JvmCompilationOperation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One run of KSP and kotlinc. Everything that touches a Kotlin Build Tools API type is here, loaded
 * only when it is present; what touches a KSP type is in {@link KspCompilation}, entered only when
 * KSP is present.
 *
 * <p>The sources to compile are the closure {@link StagedSourceCompiler} computed from the dependency
 * index, compiled against the compile classpath and the previous output with the hidden classes left
 * out; the incremental caches of the Kotlin compiler itself are not used yet. KSP runs first, over the
 * same sources, incrementally with its own caches beside the class output. What it generated is
 * credited to the sources it was generated for: the Kotlin sources are compiled with the batch and the
 * Java sources through the JDK's compiler, and the class files and resources are staged with the
 * compiled classes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class KotlinCompilation {

    private static final Logger LOG = LoggerFactory.getLogger(KotlinCompilation.class);
    private static final String KSP_IMPLEMENTATION = "com.google.devtools.ksp.impl.KotlinSymbolProcessing";
    private static final String PROVIDER_SERVICE = "META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider";
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)", Pattern.MULTILINE);
    private static final String MODULE_NAME = "main";

    @Nullable
    private static volatile KotlinToolchains toolchains;

    private KotlinCompilation() {
    }

    /**
     * @return Whether an implementation of the Build Tools API can be loaded
     */
    static boolean isAvailable() {
        try {
            return toolchains() != null;
        } catch (NoImplementationFoundException | LinkageError e) {
            LOG.debug("The Kotlin Build Tools API has no implementation on the classpath: {}", e.getMessage());
            return false;
        }
    }

    /**
     * @return Whether the KSP implementation is on the classpath, checked here so that {@link KspCompilation} is
     *         never touched without it
     */
    static boolean kspPresent() {
        return io.micronaut.core.reflect.ClassUtils.isPresent(KSP_IMPLEMENTATION, KotlinCompilation.class.getClassLoader());
    }

    private static KotlinToolchains toolchains() {
        KotlinToolchains loaded = toolchains;
        if (loaded == null) {
            // the compiler keeps its warm state for the life of the JVM
            loaded = KotlinToolchains.loadImplementation(KotlinCompilation.class.getClassLoader());
            toolchains = loaded;
        }
        return loaded;
    }

    /**
     * Compiles the sources into the staging directory.
     *
     * @return What was produced, or null when the compilation failed
     */
    static StagedSourceCompiler.@Nullable Produced run(CompilationRequest request, Set<Path> toCompile, Set<String> hiddenClasses, Path staging, Path generatedStaging, List<CompileDiagnostic> diagnostics) throws IOException {
        List<Path> classpath = new ArrayList<>(request.compileClasspath());
        Path visibleOutput = visibleOutput(request, hiddenClasses, staging);
        if (visibleOutput != null) {
            classpath.add(visibleOutput);
        }
        @Nullable KspRun ksp = runKsp(request, toCompile, classpath, diagnostics);
        if (ksp != null && !ksp.succeeded()) {
            return null;
        }
        // every source compiled, own or generated, with the sources its classes are credited to
        Map<Path, Set<Path>> originsBySource = new LinkedHashMap<>();
        for (Path source : toCompile) {
            originsBySource.put(source, Set.of(source));
        }
        Map<Path, Set<Path>> generatedKotlin = ksp != null ? ksp.under("kotlin") : Map.of();
        Map<Path, Set<Path>> generatedJava = ksp != null ? ksp.under("java") : Map.of();
        originsBySource.putAll(generatedKotlin);
        originsBySource.putAll(generatedJava);
        List<Path> kotlinSources = new ArrayList<>(toCompile);
        kotlinSources.addAll(generatedKotlin.keySet());
        List<Path> javaRoots = javaRoots(request);
        if (ksp != null && !generatedJava.isEmpty()) {
            // a Kotlin source may reference what the processors generated in Java: kotlinc reads it as source
            javaRoots.add(ksp.work().resolve("java"));
        }
        // a batch that only deletes has no source of its own: the processors ran, and there is nothing to compile
        if (!kotlinSources.isEmpty() && !compile(request, kotlinSources, javaRoots, classpath, staging, diagnostics)) {
            return null;
        }
        if (!generatedJava.isEmpty() && !compileJava(generatedJava.keySet(), javaRoots(request), classpath, staging, diagnostics)) {
            return null;
        }
        Map<Path, Set<String>> classes = credit(staging, originsBySource);
        Map<Path, Set<String>> generatedSources = new LinkedHashMap<>();
        Map<Path, Set<String>> resources = new LinkedHashMap<>();
        if (ksp != null) {
            stage(ksp, staging, generatedStaging, classes, generatedSources, resources);
        }
        return new StagedSourceCompiler.Produced(classes, generatedSources, resources);
    }

    /**
     * Runs KSP when it is present and the processor path holds processors; a processor path holding
     * processors without KSP present is an error, since the sources expect them.
     */
    @Nullable
    private static KspRun runKsp(CompilationRequest request, Set<Path> toCompile, List<Path> classpath, List<CompileDiagnostic> diagnostics) throws IOException {
        URL[] processorPath = toUrls(request.processorPath());
        if (!kspPresent()) {
            try (URLClassLoader alone = new URLClassLoader(processorPath, null)) {
                if (alone.getResources(PROVIDER_SERVICE).hasMoreElements()) {
                    diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "The processor path holds symbol processors but KSP is not on the classpath: development mode runs KSP through symbol-processing-aa-embeddable at the project's KSP version", null, 0, 0));
                    return new KspRun(false, request.classOutput(), Map.of());
                }
            }
            return null;
        }
        try (ProcessorPathLoader processorLoader = new ProcessorPathLoader(processorPath, KotlinCompilation.class.getClassLoader())) {
            return KspCompilation.run(request, toCompile, classpath, processorLoader, MODULE_NAME, diagnostics);
        }
    }

    /**
     * The previous output as the compiler may see it: the output itself when nothing is hidden, and
     * otherwise a directory of links to its files minus the class files of the hidden classes, since a
     * classpath entry cannot hide a class the way a loader can.
     */
    @Nullable
    private static Path visibleOutput(CompilationRequest request, Set<String> hiddenClasses, Path staging) throws IOException {
        Path classOutput = request.classOutput();
        if (!Files.isDirectory(classOutput)) {
            return null;
        }
        if (hiddenClasses.isEmpty()) {
            return classOutput;
        }
        Path visible = staging.resolveSibling(staging.getFileName() + "-visible");
        OutputTransaction.deleteRecursively(visible);
        Files.createDirectories(visible);
        try (Stream<Path> files = Files.walk(classOutput)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                Path relative = classOutput.relativize(file);
                if (relative.toString().endsWith(".class") && hiddenClasses.contains(ClassDependencyIndex.topLevelClassOf(relative))) {
                    continue;
                }
                Path target = visible.resolve(relative);
                Files.createDirectories(target.getParent());
                link(file, target);
            }
        }
        return visible;
    }

    private static void link(Path file, Path target) throws IOException {
        try {
            Files.createLink(target, file);
        } catch (IOException | UnsupportedOperationException e) {
            Files.copy(file, target);
        }
    }

    private static boolean compile(CompilationRequest request, List<Path> sources, List<Path> javaRoots, List<Path> classpath, Path staging, List<CompileDiagnostic> diagnostics) {
        KotlinToolchains kotlin = toolchains();
        JvmPlatformToolchain jvm = kotlin.getToolchain(JvmPlatformToolchain.class);
        JvmCompilationOperation.Builder operation = jvm.jvmCompilationOperationBuilder(sources, staging);
        List<String> arguments = new ArrayList<>();
        arguments.add("-module-name");
        arguments.add(MODULE_NAME);
        // the project's own standard library is on the compile classpath, as it is for its build
        arguments.add("-no-stdlib");
        arguments.add("-no-reflect");
        arguments.add("-classpath");
        arguments.add(join(classpath));
        if (!javaRoots.isEmpty()) {
            arguments.add("-Xjava-source-roots=" + join(javaRoots));
        }
        for (String option : request.options()) {
            // the processor options went to KSP; kotlinc rejects them
            if (!option.startsWith("-A")) {
                arguments.add(option);
            }
        }
        operation.getCompilerArguments().applyArgumentStrings(arguments);
        operation.set(BaseCompilationOperation.COMPILER_MESSAGE_RENDERER, (severity, message, location) -> {
            CompileDiagnostic.Severity mapped = switch (severity) {
                case ERROR -> CompileDiagnostic.Severity.ERROR;
                case WARNING -> CompileDiagnostic.Severity.WARNING;
                default -> null;
            };
            if (mapped != null) {
                Path file = location != null ? Path.of(location.getPath()) : null;
                diagnostics.add(new CompileDiagnostic(mapped, message, file, location != null ? location.getLine() : 0, location != null ? location.getColumn() : 0));
            }
            return severity.name().toLowerCase() + ": " + message;
        });
        try (KotlinToolchains.BuildSession session = kotlin.createBuildSession()) {
            CompilationResult result = session.executeOperation(operation.build(), kotlin.createInProcessExecutionPolicy(), new CompilerLog());
            if (result != CompilationResult.COMPILATION_SUCCESS && diagnostics.stream().noneMatch(d -> d.severity() == CompileDiagnostic.Severity.ERROR)) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "Kotlin compilation failed: " + result, null, 0, 0));
            }
            return result == CompilationResult.COMPILATION_SUCCESS;
        }
    }

    /**
     * Compiles the Java sources the processors generated, against the classes just compiled, with no
     * processing: what the processors had to say about them they said in their own run.
     */
    private static boolean compileJava(Set<Path> sources, List<Path> javaRoots, List<Path> classpath, Path staging, List<CompileDiagnostic> diagnostics) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<Path> fullClasspath = new ArrayList<>(classpath);
        fullClasspath.add(staging);
        try (StandardJavaFileManager fileManager = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            List<String> options = new ArrayList<>(List.of("-proc:none", "-d", staging.toString(), "-classpath", join(fullClasspath)));
            if (!javaRoots.isEmpty()) {
                // a generated class may reference a Java type of the module whose class is in another output
                options.add("-sourcepath");
                options.add(join(javaRoots));
                options.add("-implicit:none");
            }
            boolean success = javac.getTask(null, fileManager, diagnostic -> {
                CompileDiagnostic.Severity severity = switch (diagnostic.getKind()) {
                    case ERROR -> CompileDiagnostic.Severity.ERROR;
                    case WARNING, MANDATORY_WARNING -> CompileDiagnostic.Severity.WARNING;
                    default -> null;
                };
                if (severity != null) {
                    JavaFileObject source = diagnostic.getSource();
                    Path file = source != null ? Path.of(source.toUri()) : null;
                    diagnostics.add(new CompileDiagnostic(severity, diagnostic.getMessage(null), file, (int) diagnostic.getLineNumber(), (int) diagnostic.getColumnNumber()));
                }
            }, options, null, units).call();
            if (!success && diagnostics.stream().noneMatch(d -> d.severity() == CompileDiagnostic.Severity.ERROR)) {
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "Compilation of the generated Java sources failed", null, 0, 0));
            }
            return success;
        }
    }

    private static List<Path> javaRoots(CompilationRequest request) {
        List<Path> roots = new ArrayList<>();
        for (SourceRoot root : request.sourceRoots()) {
            if (root.kind() == SourceKind.JAVA && Files.isDirectory(root.path())) {
                roots.add(root.path());
            }
        }
        return roots;
    }

    private static String join(List<Path> paths) {
        StringBuilder joined = new StringBuilder();
        for (Path path : paths) {
            if (!joined.isEmpty()) {
                joined.append(File.pathSeparatorChar);
            }
            joined.append(path);
        }
        return joined.toString();
    }

    /**
     * Credits every class file the compilers wrote to the sources of the file it names in its
     * {@code SourceFile} attribute: the compiled source of that file name whose package the class
     * belongs to, itself when it is one of the batch's own and the sources it was generated for when
     * it is a generated one.
     */
    private static Map<Path, Set<String>> credit(Path staging, Map<Path, Set<Path>> originsBySource) throws IOException {
        Map<String, List<Path>> byFileName = new HashMap<>();
        Map<Path, String> packages = new HashMap<>();
        for (Path source : originsBySource.keySet()) {
            byFileName.computeIfAbsent(source.getFileName().toString(), n -> new ArrayList<>()).add(source);
            Matcher matcher = PACKAGE.matcher(Files.readString(source, StandardCharsets.UTF_8));
            packages.put(source, matcher.find() ? matcher.group(1) : "");
        }
        Map<Path, Set<String>> classes = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(staging)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith(".class"))::iterator) {
                Path relative = staging.relativize(file);
                String topLevel = ClassDependencyIndex.topLevelClassOf(relative);
                String packageName = topLevel.lastIndexOf('.') < 0 ? "" : topLevel.substring(0, topLevel.lastIndexOf('.'));
                String sourceFile = sourceFileOf(file);
                Path source = null;
                for (Path candidate : byFileName.getOrDefault(sourceFile, List.of())) {
                    if (packageName.equals(packages.getOrDefault(candidate, ""))) {
                        source = candidate;
                        break;
                    }
                }
                if (source == null) {
                    // a class whose source the compiler did not name: it stays with the output, credited to no source
                    LOG.debug("No compiled source for {} (SourceFile {})", relative, sourceFile);
                    continue;
                }
                for (Path origin : originsBySource.getOrDefault(source, Set.of())) {
                    classes.computeIfAbsent(origin, s -> new LinkedHashSet<>()).add(topLevel);
                }
            }
        }
        return classes;
    }

    @Nullable
    private static String sourceFileOf(Path classFile) throws IOException {
        return ClassFile.of().parse(Files.readAllBytes(classFile))
            .findAttribute(Attributes.sourceFile())
            .map(SourceFileAttribute::sourceFile)
            .map(name -> name.stringValue())
            .orElse(null);
    }

    /**
     * Stages what the processors created in this run beside the compiled classes, credited to the
     * sources it was created for: the class files, the resources, and the generated sources as a
     * record in the generated sources directory, so that they go when their sources go.
     */
    private static void stage(KspRun ksp, Path staging, Path generatedStaging, Map<Path, Set<String>> classes, Map<Path, Set<String>> generatedSources, Map<Path, Set<String>> resources) throws IOException {
        Path classOutput = ksp.work().resolve("classes");
        for (Map.Entry<Path, Set<Path>> entry : ksp.under("classes").entrySet()) {
            Path relative = classOutput.relativize(entry.getKey());
            copy(entry.getKey(), staging.resolve(relative));
            if (relative.toString().endsWith(".class")) {
                for (Path origin : entry.getValue()) {
                    classes.computeIfAbsent(origin, s -> new LinkedHashSet<>()).add(ClassDependencyIndex.topLevelClassOf(relative));
                }
            }
        }
        Path resourceOutput = ksp.work().resolve("resources");
        for (Map.Entry<Path, Set<Path>> entry : ksp.under("resources").entrySet()) {
            Path relative = resourceOutput.relativize(entry.getKey());
            copy(entry.getKey(), staging.resolve(relative));
            for (Path origin : entry.getValue()) {
                resources.computeIfAbsent(origin, s -> new LinkedHashSet<>()).add(relative.toString());
            }
        }
        for (String language : List.of("kotlin", "java")) {
            Path sourceOutput = ksp.work().resolve(language);
            for (Map.Entry<Path, Set<Path>> entry : ksp.under(language).entrySet()) {
                Path relative = sourceOutput.relativize(entry.getKey());
                copy(entry.getKey(), generatedStaging.resolve(relative));
                for (Path origin : entry.getValue()) {
                    generatedSources.computeIfAbsent(origin, s -> new LinkedHashSet<>()).add(relative.toString());
                }
            }
        }
    }

    private static void copy(Path file, Path target) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static URL[] toUrls(List<Path> classpath) {
        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = classpath.get(i).toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Not a classpath entry: " + classpath.get(i), e);
            }
        }
        return urls;
    }

    /**
     * The loader of the processor path: the classes resolve through the parent, the API and the launch classpath,
     * and the provider service files come from the processor path alone, so that a provider elsewhere on the
     * launch classpath is not run as if the project had configured it.
     */
    private static final class ProcessorPathLoader extends URLClassLoader {

        static {
            registerAsParallelCapable();
        }

        ProcessorPathLoader(URL[] urls, @Nullable ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public java.util.Enumeration<URL> getResources(String name) throws IOException {
            if (PROVIDER_SERVICE.equals(name)) {
                return findResources(name);
            }
            return super.getResources(name);
        }

        @Override
        @Nullable
        public URL getResource(String name) {
            if (PROVIDER_SERVICE.equals(name)) {
                return findResource(name);
            }
            return super.getResource(name);
        }
    }

    /**
     * Where the compiler's own messages go: the diagnostics come through the renderer, the rest to the log.
     */
    private static final class CompilerLog implements KotlinLogger {

        @Override
        public boolean isDebugEnabled() {
            return LOG.isDebugEnabled();
        }

        @Override
        public void error(String message, @Nullable Throwable throwable) {
            LOG.debug("kotlinc: {}", message, throwable);
        }

        @Override
        public void warn(String message, @Nullable Throwable throwable) {
            LOG.debug("kotlinc: {}", message, throwable);
        }

        @Override
        public void info(String message) {
            LOG.debug("kotlinc: {}", message);
        }

        @Override
        public void debug(String message) {
            LOG.trace("kotlinc: {}", message);
        }

        @Override
        public void lifecycle(String message) {
            LOG.debug("kotlinc: {}", message);
        }
    }
}
