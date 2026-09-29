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

import com.google.devtools.ksp.impl.KotlinSymbolProcessing;
import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.KSPJvmConfig;
import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import com.google.devtools.ksp.symbol.FileLocation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSFunctionDeclaration;
import com.google.devtools.ksp.symbol.KSNode;
import com.google.devtools.ksp.symbol.KSPropertyDeclaration;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One run of KSP over the sources of a batch. Everything that touches a KSP type is here, loaded only
 * when KSP is present.
 *
 * <p>The processors run through a {@link CodeGenerator} that records, for every file they create, the
 * sources it was created for, so that a generated class, source or resource is credited to them and
 * goes when they go.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class KspCompilation {

    private static final Logger LOG = LoggerFactory.getLogger(KspCompilation.class);
    private static final String KSP_COMPILER_VERSION = "META-INF/ksp.compiler.version";
    private static final String DEFAULT_JVM_TARGET = "21";

    private KspCompilation() {
    }

    /**
     * Runs the given processors.
     *
     * @param request The request
     * @param toCompile The sources of the batch
     * @param classpath The compile classpath, with the previous output as the compiler may see it
     * @param processorLoader The loader of the processor path, the providers are loaded from
     * @param moduleName The module name
     * @param diagnostics Where the processors' errors and warnings go
     * @return The run, or null when the processor path holds no processor
     */
    @Nullable
    static KspRun run(CompilationRequest request, Set<Path> toCompile, List<Path> classpath, ClassLoader processorLoader, String moduleName, List<CompileDiagnostic> diagnostics) throws IOException {
        List<SymbolProcessorProvider> providers = new ArrayList<>();
        // the loader answers the service lookup from the processor path alone: a provider elsewhere on the launch
        // classpath is not one the project configured
        java.util.ServiceLoader.load(SymbolProcessorProvider.class, processorLoader).forEach(providers::add);
        if (providers.isEmpty()) {
            return null;
        }
        Path work = KotlinSourceCompiler.kspWork(request);
        Path classes = work.resolve("classes");
        Path kotlin = work.resolve("kotlin");
        Path java = work.resolve("java");
        Path resources = work.resolve("resources");
        Path caches = work.resolve("caches");
        if (request.isFull()) {
            OutputTransaction.deleteRecursively(work);
        }
        for (Path directory : List.of(classes, kotlin, java, resources, caches)) {
            Files.createDirectories(directory);
        }
        KSPJvmConfig.Builder config = new KSPJvmConfig.Builder();
        config.setModuleName(moduleName);
        List<File> kotlinRoots = new ArrayList<>();
        List<File> javaRoots = new ArrayList<>();
        for (SourceRoot root : request.sourceRoots()) {
            if (!Files.isDirectory(root.path())) {
                continue;
            }
            if (root.kind() == SourceKind.KOTLIN) {
                kotlinRoots.add(root.path().toFile());
            } else if (root.kind() == SourceKind.JAVA) {
                javaRoots.add(root.path().toFile());
            }
        }
        config.setSourceRoots(kotlinRoots);
        config.setJavaSourceRoots(javaRoots);
        config.setProjectBaseDir(work.toFile());
        config.setOutputBaseDir(work.toFile());
        config.setCachesDir(caches.toFile());
        config.setClassOutputDir(classes.toFile());
        config.setKotlinOutputDir(kotlin.toFile());
        config.setJavaOutputDir(java.toFile());
        config.setResourceOutputDir(resources.toFile());
        config.setLibraries(classpath.stream().map(Path::toFile).toList());
        config.setJvmTarget(option(request.options(), "-jvm-target", DEFAULT_JVM_TARGET));
        String version = compilerLanguageVersion();
        config.setLanguageVersion(option(request.options(), "-language-version", version));
        config.setApiVersion(option(request.options(), "-api-version", version));
        config.setProcessorOptions(processorOptions(request.options()));
        config.setIncremental(!request.isFull());
        config.setModifiedSources(toCompile.stream().map(Path::toFile).toList());
        config.setRemovedSources(request.deleted().stream().map(Path::toFile).toList());
        config.setChangedClasses(new ArrayList<>(request.affectedClasses()));
        Log log = new Log(diagnostics);
        Outputs outputs = new Outputs(work, () -> kotlinSources(request));
        List<SymbolProcessorProvider> recording = new ArrayList<>(providers.size());
        for (SymbolProcessorProvider provider : providers) {
            recording.add(environment -> provider.create(outputs.record(environment)));
        }
        KotlinSymbolProcessing.ExitCode exit = new KotlinSymbolProcessing(config.build(), recording, log).execute();
        boolean succeeded = exit == KotlinSymbolProcessing.ExitCode.OK && !log.failed;
        if (!succeeded && diagnostics.stream().noneMatch(d -> d.severity() == CompileDiagnostic.Severity.ERROR)) {
            diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "Symbol processing failed: " + exit, null, 0, 0));
        }
        return new KspRun(succeeded, work, outputs.originsByOutput);
    }

    /**
     * Every Kotlin source of the module: what an output created for all sources is credited to, so that it
     * goes when any of them goes and is regenerated by the run that sees that change.
     */
    private static Set<Path> kotlinSources(CompilationRequest request) {
        Set<Path> sources = new LinkedHashSet<>();
        for (SourceRoot root : request.sourceRoots()) {
            if (root.kind() != SourceKind.KOTLIN || !Files.isDirectory(root.path())) {
                continue;
            }
            try (java.util.stream.Stream<Path> files = Files.walk(root.path())) {
                files.filter(Files::isRegularFile).filter(SourceKind.KOTLIN::matches).forEach(file -> sources.add(file.toAbsolutePath().normalize()));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("Cannot scan " + root.path(), e);
            }
        }
        return sources;
    }

    /**
     * The options the processors receive: every {@code -Akey=value}, as the build passes processor
     * arguments to javac, since a Micronaut project configures its processors the same way for both.
     */
    static Map<String, String> processorOptions(List<String> options) {
        Map<String, String> processorOptions = new LinkedHashMap<>();
        for (String option : options) {
            if (option.startsWith("-A")) {
                int equals = option.indexOf('=');
                if (equals > 2) {
                    processorOptions.put(option.substring(2, equals), option.substring(equals + 1));
                } else {
                    processorOptions.put(option.substring(2), "");
                }
            }
        }
        return processorOptions;
    }

    /**
     * The language version KSP's own compiler speaks, from the version the KSP implementation records.
     */
    private static String compilerLanguageVersion() {
        try (InputStream in = KspCompilation.class.getClassLoader().getResourceAsStream(KSP_COMPILER_VERSION)) {
            if (in != null) {
                Matcher matcher = Pattern.compile("^(\\d+\\.\\d+)").matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (IOException e) {
            LOG.debug("Cannot read {}: {}", KSP_COMPILER_VERSION, e.getMessage());
        }
        return "2.0";
    }

    private static String option(List<String> options, String name, String defaultValue) {
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            if (option.equals(name) && i + 1 < options.size()) {
                return options.get(i + 1);
            }
            if (option.startsWith(name + "=")) {
                return option.substring(name.length() + 1);
            }
        }
        return defaultValue;
    }

    /**
     * Records what the processors create, through a code generator wrapped around KSP's own.
     */
    private static final class Outputs {

        private final Path work;
        private final java.util.function.Supplier<Set<Path>> allSources;
        private final Map<Path, Set<Path>> originsByOutput = new LinkedHashMap<>();
        private final Set<File> seen = new HashSet<>();
        @Nullable
        private Set<Path> everySource;

        Outputs(Path work, java.util.function.Supplier<Set<Path>> allSources) {
            this.work = work;
            this.allSources = allSources;
        }

        SymbolProcessorEnvironment record(SymbolProcessorEnvironment environment) {
            CodeGenerator recording = new RecordingCodeGenerator(environment.getCodeGenerator());
            try {
                return new SymbolProcessorEnvironment(environment.getOptions(), environment.getKotlinVersion(), recording, environment.getLogger(),
                    environment.getApiVersion(), environment.getCompilerVersion(), environment.getPlatforms(), environment.getKspVersion());
            } catch (LinkageError e) {
                // another KSP: the processors run as they are, and what they generate stays with the output uncredited
                LOG.debug("Cannot wrap the symbol processor environment of this KSP, generated files are not credited to their sources: {}", e.getMessage());
                return environment;
            }
        }

        private synchronized void created(Collection<File> generated, Dependencies dependencies) {
            Set<Path> origins = new LinkedHashSet<>();
            if (dependencies.isAllSources()) {
                // an output over every source belongs to every source
                if (everySource == null) {
                    everySource = allSources.get();
                }
                origins.addAll(everySource);
            }
            for (KSFile file : dependencies.getOriginatingFiles()) {
                origins.add(Path.of(file.getFilePath()).toAbsolutePath().normalize());
            }
            for (File file : generated) {
                if (seen.add(file)) {
                    originsByOutput.put(file.toPath().toAbsolutePath().normalize(), origins);
                }
            }
        }

        private synchronized void associated(List<? extends KSFile> sources, Path output) {
            Set<Path> origins = originsByOutput.computeIfAbsent(output.toAbsolutePath().normalize(), o -> new LinkedHashSet<>());
            for (KSFile file : sources) {
                origins.add(Path.of(file.getFilePath()).toAbsolutePath().normalize());
            }
        }

        private Path outputOf(String packageName, String fileName, String extension) {
            Path directory = switch (extension) {
                case "kt" -> work.resolve("kotlin");
                case "java" -> work.resolve("java");
                case "class" -> work.resolve("classes");
                default -> work.resolve("resources");
            };
            Path file = directory.resolve(packageName.replace('.', File.separatorChar));
            return file.resolve(extension.isEmpty() ? fileName : fileName + "." + extension);
        }

        /**
         * KSP's code generator, recording what it creates.
         */
        private final class RecordingCodeGenerator implements CodeGenerator {

            private final CodeGenerator delegate;

            RecordingCodeGenerator(CodeGenerator delegate) {
                this.delegate = delegate;
            }

            @Override
            public OutputStream createNewFile(Dependencies dependencies, String packageName, String fileName, String extensionName) {
                OutputStream stream = delegate.createNewFile(dependencies, packageName, fileName, extensionName);
                created(delegate.getGeneratedFile(), dependencies);
                return stream;
            }

            @Override
            public OutputStream createNewFileByPath(Dependencies dependencies, String path, String extensionName) {
                OutputStream stream = delegate.createNewFileByPath(dependencies, path, extensionName);
                created(delegate.getGeneratedFile(), dependencies);
                return stream;
            }

            @Override
            public void associate(List<? extends KSFile> sources, String packageName, String fileName, String extensionName) {
                delegate.associate(sources, packageName, fileName, extensionName);
                associated(sources, outputOf(packageName, fileName, extensionName));
            }

            @Override
            public void associateByPath(List<? extends KSFile> sources, String path, String extensionName) {
                delegate.associateByPath(sources, path, extensionName);
                associated(sources, outputOf("", path, extensionName));
            }

            @Override
            public void associateWithClasses(List<? extends KSClassDeclaration> classes, String packageName, String fileName, String extensionName) {
                delegate.associateWithClasses(classes, packageName, fileName, extensionName);
                List<KSFile> files = new ArrayList<>();
                for (KSClassDeclaration declaration : classes) {
                    if (declaration.getContainingFile() != null) {
                        files.add(declaration.getContainingFile());
                    }
                }
                associated(files, outputOf(packageName, fileName, extensionName));
            }

            @Override
            public void associateWithFunctions(List<? extends KSFunctionDeclaration> functions, String packageName, String fileName, String extensionName) {
                delegate.associateWithFunctions(functions, packageName, fileName, extensionName);
                List<KSFile> files = new ArrayList<>();
                for (KSFunctionDeclaration declaration : functions) {
                    if (declaration.getContainingFile() != null) {
                        files.add(declaration.getContainingFile());
                    }
                }
                associated(files, outputOf(packageName, fileName, extensionName));
            }

            @Override
            public void associateWithProperties(List<? extends KSPropertyDeclaration> properties, String packageName, String fileName, String extensionName) {
                delegate.associateWithProperties(properties, packageName, fileName, extensionName);
                List<KSFile> files = new ArrayList<>();
                for (KSPropertyDeclaration declaration : properties) {
                    if (declaration.getContainingFile() != null) {
                        files.add(declaration.getContainingFile());
                    }
                }
                associated(files, outputOf(packageName, fileName, extensionName));
            }

            @Override
            public Collection<File> getGeneratedFile() {
                return delegate.getGeneratedFile();
            }
        }
    }

    /**
     * Where the processors' messages go: errors and warnings to the diagnostics, the rest to the log.
     */
    private static final class Log implements KSPLogger {

        private final List<CompileDiagnostic> diagnostics;
        private boolean failed;

        Log(List<CompileDiagnostic> diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public void logging(String message, @Nullable KSNode node) {
            LOG.trace("ksp: {}", message);
        }

        @Override
        public void info(String message, @Nullable KSNode node) {
            LOG.debug("ksp: {}", message);
        }

        @Override
        public void warn(String message, @Nullable KSNode node) {
            diagnostics.add(diagnostic(CompileDiagnostic.Severity.WARNING, message, node));
        }

        @Override
        public void error(String message, @Nullable KSNode node) {
            failed = true;
            diagnostics.add(diagnostic(CompileDiagnostic.Severity.ERROR, message, node));
        }

        @Override
        public void exception(Throwable throwable) {
            failed = true;
            diagnostics.add(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, String.valueOf(throwable.getMessage()), null, 0, 0));
        }

        private static CompileDiagnostic diagnostic(CompileDiagnostic.Severity severity, String message, @Nullable KSNode node) {
            if (node != null && node.getLocation() instanceof FileLocation location) {
                return new CompileDiagnostic(severity, message, Path.of(location.getFilePath()), location.getLineNumber(), 0);
            }
            return new CompileDiagnostic(severity, message, null, 0, 0);
        }
    }
}
