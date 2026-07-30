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
package io.micronaut.python.compiler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.version.VersionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Plans and records an incremental {@link PyronautCompiler} disk compilation.
 */
@Internal
final class IncrementalCompilation {
    private static final String STATE_FILE = "state.properties";
    private static final String STATE_VERSION = "6";
    private static final String SOURCE_PREFIX = "source.";
    private static final String VFS_SOURCE_PREFIX = "META-INF/GRAALPY-VFS/micronaut-application/src/";
    private static final String VFS_ROOT = "META-INF/GRAALPY-VFS/micronaut-application";
    private static final String PYTHON_INIT_MODULE_SUFFIX = ".__init__";
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern JAVA_TYPE = Pattern.compile("\\b(?:class|interface|record|enum|@interface)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PYTHON_TYPE = Pattern.compile("(?m)^\\s*class\\s+([A-Za-z_]\\w*)\\b");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final Pattern PYTHON_FROM_IMPORT = Pattern.compile("(?m)^\\s*from\\s+(\\S+)\\s+import\\s+");
    private static final Pattern PYTHON_DIRECT_IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)");
    private static final Pattern PYTHON_STAR_IMPORT = Pattern.compile("(?m)^\\s*from\\s+\\S+\\s+import\\s+\\*");
    private static final Pattern DYNAMIC_PYTHON_REFERENCE = Pattern.compile(
        "\\b(?:__import__|import_module|getattr|globals|locals)\\s*\\(|@\\s*Mixin\\b"
    );

    private final Path javaRoot;
    private final List<Path> pythonRoots;
    private final String pythonCode;
    private final String packageName;
    private final String applicationClass;
    private final Path targetDirectory;
    private final Path cacheDirectory;
    private final List<File> classpath;
    private final List<File> bootclasspath;
    private final List<File> annotationProcessorPath;
    private final List<String> compilerOptions;
    private final boolean compilePythonBytecode;
    private final List<String> processorTypes;
    private final PythonIncrementalMode pythonIncrementalMode;
    private final String globalFingerprint;

    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"})
    IncrementalCompilation(String javaSrc,
                           String pythonSrc,
                           String pythonCode,
                           String packageName,
                           String applicationClass,
                           File targetDirectory,
                           File cacheDirectory,
                           List<File> classpath,
                           List<File> bootclasspath,
                           List<File> annotationProcessorPath,
                           List<String> compilerOptions,
                           boolean compilePythonBytecode,
                           Collection<?> annotationProcessors,
                           Collection<?> pythonSourceVisitors,
                           PythonIncrementalMode pythonIncrementalMode) {
        this.javaRoot = normalizeNullable(javaSrc);
        this.pythonRoots = parseRoots(pythonSrc);
        this.pythonCode = pythonCode;
        this.packageName = packageName;
        this.applicationClass = applicationClass;
        this.targetDirectory = targetDirectory.toPath().toAbsolutePath().normalize();
        this.cacheDirectory = cacheDirectory.toPath().toAbsolutePath().normalize();
        this.classpath = copy(classpath);
        this.bootclasspath = copy(bootclasspath);
        this.annotationProcessorPath = copy(annotationProcessorPath);
        this.compilerOptions = compilerOptions == null ? List.of() : List.copyOf(compilerOptions);
        this.compilePythonBytecode = compilePythonBytecode;
        this.pythonIncrementalMode = pythonIncrementalMode;
        this.processorTypes = Stream.concat(annotationProcessors.stream(), pythonSourceVisitors.stream())
            .map(value -> value.getClass().getName())
            .sorted()
            .toList();
        validateDirectories();
        this.globalFingerprint = fingerprintGlobalInputs();
    }

    Plan plan(Set<String> detectedAggregatingInputs) {
        Map<String, ScannedSource> scannedSources = scanSources();
        Map<String, SourceState> currentSources = createSourceStates(scannedSources);
        Set<String> currentAggregatingInputs = detectedAggregatingInputs.stream()
            .filter(currentSources::containsKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean hasAggregatingProcessors = !currentAggregatingInputs.isEmpty();
        State previous = readState();
        if (previous == null
            || !globalFingerprint.equals(previous.globalFingerprint())
            || !previous.processorCompatible()
            || hasMissingOutputs(previous)) {
            return new Plan(
                false,
                true,
                Set.copyOf(currentSources.keySet()),
                Set.copyOf(currentSources.keySet()),
                currentSources,
                previous,
                hasAggregatingProcessors,
                currentAggregatingInputs,
                hasAggregatingProcessors
            );
        }

        Set<String> changed = changedSources(previous.sources(), currentSources);
        if (changed.isEmpty()) {
            return new Plan(
                true,
                false,
                Set.of(),
                Set.of(),
                currentSources,
                previous,
                false,
                currentAggregatingInputs,
                hasAggregatingProcessors
            );
        }

        Set<String> affected = affectedSources(changed, previous.sources(), currentSources);
        if ((pythonIncrementalMode == PythonIncrementalMode.CONSERVATIVE
            && requiresConservativePythonProcessing(affected, scannedSources))
            || containsDeletedPythonSource(affected, currentSources, previous.sources())) {
            currentSources.values().stream()
                .filter(source -> source.language() == Language.PYTHON)
                .map(SourceState::key)
                .forEach(affected::add);
        }
        addPythonPackageInitializers(affected, currentSources);
        Set<String> isolatingAffected = Set.copyOf(affected);
        Set<String> aggregationTriggers = new LinkedHashSet<>(previous.aggregatingInputs());
        aggregationTriggers.addAll(currentAggregatingInputs);
        boolean aggregating = affected.stream().anyMatch(aggregationTriggers::contains);
        if (aggregating) {
            affected.addAll(currentAggregatingInputs);
        }
        addApplicationSourceWhenPythonChanges(affected, isolatingAffected, currentSources, previous.sources());
        return new Plan(
            false,
            false,
            Set.copyOf(affected),
            isolatingAffected,
            currentSources,
            previous,
            aggregating,
            currentAggregatingInputs,
            hasAggregatingProcessors
        );
    }

    Plan plan(boolean hasAggregatingProcessors) {
        Map<String, ScannedSource> scannedSources = scanSources();
        return plan(hasAggregatingProcessors ? scannedSources.keySet() : Set.of());
    }

    void prepareOutput(Plan plan) {
        try {
            if (plan.fullRebuild()) {
                deleteTree(targetDirectory);
                Files.createDirectories(targetDirectory);
                return;
            }
            State previous = plan.previous();
            if (previous == null) {
                Files.createDirectories(targetDirectory);
                return;
            }
            Set<String> outputs = new LinkedHashSet<>();
            for (String source : plan.isolatingAffectedSources()) {
                SourceState sourceState = previous.sources().get(source);
                if (sourceState != null) {
                    outputs.addAll(sourceState.outputs());
                }
            }
            if (plan.aggregating()) {
                outputs.addAll(previous.aggregatingOutputs());
                outputs.removeAll(previous.pythonAggregatingOutputs());
            }
            for (String output : outputs) {
                deleteManagedOutput(output);
            }
            Files.createDirectories(targetDirectory);
        } catch (IOException e) {
            throw new PyronautCompilerException("Failed to prepare incremental compilation output: " + e.getMessage());
        }
    }

    void complete(Plan plan, IncrementalCompilationTrace compilationTrace) {
        try {
            rebuildPythonFilesList();
            Map<String, SourceState> enrichedSources = mergeCompilationTrace(plan, compilationTrace);
            Set<String> outputFiles = listOutputs();
            Map<String, SourceState> sources = assignOutputs(
                enrichedSources,
                outputFiles,
                compilationTrace.outputs(),
                compilationTrace.aggregatingOutputs(),
                compilationTrace.pythonProcessorOutputs(),
                compilationTrace.contractViolatingOutputs()
            );
            Set<String> assigned = new HashSet<>();
            sources.values().forEach(source -> assigned.addAll(source.outputs()));
            Set<String> aggregatingOutputs = new LinkedHashSet<>(compilationTrace.aggregatingOutputs());
            if (!plan.aggregating() && plan.previous() != null) {
                aggregatingOutputs.addAll(plan.previous().aggregatingOutputs());
            }
            aggregatingOutputs.retainAll(outputFiles);
            Set<String> pythonAggregatingOutputs = new LinkedHashSet<>(
                compilationTrace.pythonProcessorOutputs()
            );
            pythonAggregatingOutputs.removeAll(assigned);
            if (!runsPythonProcessing(plan) && plan.previous() != null) {
                pythonAggregatingOutputs.addAll(plan.previous().pythonAggregatingOutputs());
            } else if (plan.previous() != null) {
                Set<String> declaredPythonOutputs = compilationTrace.pythonProcessorOutputs();
                plan.previous().pythonAggregatingOutputs().stream()
                    .filter(output -> isBytecodeForDeclaredPythonOutput(output, declaredPythonOutputs)
                        || (!plan.aggregating() && !output.startsWith(VFS_ROOT + '/')))
                    .forEach(pythonAggregatingOutputs::add);
                Set<String> stalePythonOutputs = new LinkedHashSet<>(
                    plan.previous().pythonAggregatingOutputs()
                );
                stalePythonOutputs.removeAll(pythonAggregatingOutputs);
                for (String staleOutput : stalePythonOutputs) {
                    deleteManagedOutput(staleOutput);
                }
                outputFiles = listOutputs();
            }
            pythonAggregatingOutputs.retainAll(outputFiles);
            Set<String> sharedOutputs = new LinkedHashSet<>(outputFiles);
            sharedOutputs.removeAll(assigned);
            sharedOutputs.removeAll(aggregatingOutputs);
            sharedOutputs.removeAll(pythonAggregatingOutputs);
            Set<String> aggregatingInputs = plan.aggregatingInputs();
            if ((plan.fullRebuild() || plan.aggregating())
                && !compilationTrace.aggregatingInputs().isEmpty()) {
                aggregatingInputs = compilationTrace.aggregatingInputs();
            } else if (!plan.aggregating() && plan.previous() != null) {
                aggregatingInputs = plan.previous().aggregatingInputs();
            }
            if (aggregatingInputs.isEmpty() && !compilationTrace.aggregatingOutputs().isEmpty()) {
                aggregatingInputs = Set.copyOf(sources.keySet());
            }
            boolean detectedAggregation = !aggregatingInputs.isEmpty()
                || !compilationTrace.aggregatingOutputs().isEmpty();
            boolean processorCompatible = compilationTrace.processorCompatible()
                && compilationTrace.contractViolatingOutputs().isEmpty()
                && sharedOutputs.stream().allMatch(this::isKnownCompilerSharedOutput);
            writeState(new State(
                globalFingerprint,
                sources,
                aggregatingInputs,
                Set.copyOf(aggregatingOutputs),
                Set.copyOf(pythonAggregatingOutputs),
                Set.copyOf(sharedOutputs),
                detectedAggregation,
                processorCompatible
            ));
        } catch (IOException e) {
            invalidate();
            throw new PyronautCompilerException("Failed to record incremental compilation state: " + e.getMessage());
        }
    }

    private static Map<String, SourceState> mergeCompilationTrace(Plan plan,
                                                                  IncrementalCompilationTrace compilationTrace) {
        Map<String, SourceState> result = new LinkedHashMap<>();
        for (SourceState source : plan.currentSources().values()) {
            Set<String> dependencies = new LinkedHashSet<>(source.dependencies());
            Set<String> types = new LinkedHashSet<>(source.types());
            if (compilationTrace.analyzedSources().contains(source.key())) {
                dependencies.addAll(compilationTrace.dependencies().getOrDefault(source.key(), Set.of()));
                types.addAll(compilationTrace.declaredTypes().getOrDefault(source.key(), Set.of()));
            } else if (plan.previous() != null) {
                SourceState previous = plan.previous().sources().get(source.key());
                if (previous != null && previous.hash().equals(source.hash())) {
                    dependencies.addAll(previous.dependencies());
                    types.addAll(previous.types());
                }
            }
            result.put(source.key(), new SourceState(
                source.key(),
                source.language(),
                source.relativePath(),
                source.hash(),
                Set.copyOf(dependencies),
                Set.of(),
                Set.copyOf(types)
            ));
        }
        return result;
    }

    void invalidate() {
        try {
            Files.deleteIfExists(cacheDirectory.resolve(STATE_FILE));
        } catch (IOException ignored) {
            // A missing or unreadable state file causes a full rebuild on the next invocation.
        }
    }

    private boolean runsPythonProcessing(Plan plan) {
        if (plan.runsPythonProcessing() || applicationClass == null) {
            return plan.runsPythonProcessing();
        }
        return plan.affectedSources().stream()
            .map(plan.currentSources()::get)
            .filter(source -> source != null && source.language() == Language.JAVA)
            .anyMatch(source -> source.types().contains(applicationClass));
    }

    private void addApplicationSourceWhenPythonChanges(Set<String> affected,
                                                       Set<String> isolatingAffected,
                                                       Map<String, SourceState> currentSources,
                                                       Map<String, SourceState> previousSources) {
        boolean pythonChanged = isolatingAffected.stream()
            .map(key -> {
                SourceState source = currentSources.get(key);
                return source == null ? previousSources.get(key) : source;
            })
            .anyMatch(source -> source != null && source.language() == Language.PYTHON);
        if (!pythonChanged || applicationClass == null || applicationClass.isBlank()) {
            return;
        }
        currentSources.values().stream()
            .filter(source -> source.language() == Language.JAVA)
            .filter(source -> source.types().contains(applicationClass))
            .map(SourceState::key)
            .forEach(affected::add);
    }

    private static boolean requiresConservativePythonProcessing(Set<String> affected,
                                                                Map<String, ScannedSource> scannedSources) {
        boolean affectsPython = affected.stream()
            .map(scannedSources::get)
            .filter(java.util.Objects::nonNull)
            .anyMatch(source -> source.state().language() == Language.PYTHON);
        if (!affectsPython) {
            return false;
        }
        Set<String> pythonModules = scannedSources.values().stream()
            .filter(source -> source.state().language() == Language.PYTHON)
            .map(source -> pythonModule(source.state().relativePath()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return scannedSources.values().stream()
            .filter(source -> source.state().language() == Language.PYTHON)
            .anyMatch(source -> hasDynamicOrUnresolvedPythonRelationship(source, pythonModules));
    }

    private static boolean hasDynamicOrUnresolvedPythonRelationship(ScannedSource source,
                                                                    Set<String> pythonModules) {
        if (DYNAMIC_PYTHON_REFERENCE.matcher(source.content()).find()
            || PYTHON_STAR_IMPORT.matcher(source.content()).find()) {
            return true;
        }
        for (String module : pythonImports(source.content())) {
            if (module.startsWith(".")) {
                String resolved = resolveRelativePythonModule(source.state().relativePath(), module);
                if (!pythonModules.contains(resolved) && !pythonModules.contains(resolved + PYTHON_INIT_MODULE_SUFFIX)) {
                    return true;
                }
            } else {
                int separator = module.indexOf('.');
                String topLevelModule = separator == -1 ? module : module.substring(0, separator);
                boolean localPackage = pythonModules.stream()
                    .anyMatch(candidate -> candidate.equals(topLevelModule)
                        || candidate.startsWith(topLevelModule + '.'));
                if (localPackage
                    && !pythonModules.contains(module)
                    && !pythonModules.contains(module + PYTHON_INIT_MODULE_SUFFIX)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> pythonImports(String content) {
        Set<String> modules = new LinkedHashSet<>();
        Matcher fromImports = PYTHON_FROM_IMPORT.matcher(content);
        while (fromImports.find()) {
            modules.add(fromImports.group(1));
        }
        Matcher directImports = PYTHON_DIRECT_IMPORT.matcher(content);
        while (directImports.find()) {
            modules.add(directImports.group(1));
        }
        return modules;
    }

    private static String resolveRelativePythonModule(String relativePath, String module) {
        String currentPackage = pythonPackage(relativePath);
        int parentCount = 0;
        while (parentCount < module.length() && module.charAt(parentCount) == '.') {
            parentCount++;
        }
        String resolvedPackage = currentPackage;
        for (int i = 1; i < parentCount; i++) {
            int separator = resolvedPackage.lastIndexOf('.');
            resolvedPackage = separator == -1 ? "" : resolvedPackage.substring(0, separator);
        }
        String suffix = module.substring(parentCount);
        if (resolvedPackage.isEmpty()) {
            return suffix;
        }
        return suffix.isEmpty() ? resolvedPackage : resolvedPackage + '.' + suffix;
    }

    private static boolean containsDeletedPythonSource(Set<String> affected,
                                                       Map<String, SourceState> currentSources,
                                                       Map<String, SourceState> previousSources) {
        return affected.stream()
            .filter(Predicate.not(currentSources::containsKey))
            .map(previousSources::get)
            .anyMatch(source -> source != null && source.language() == Language.PYTHON);
    }

    private static void addPythonPackageInitializers(Set<String> affected,
                                                     Map<String, SourceState> currentSources) {
        boolean affectsPython = affected.stream()
            .map(currentSources::get)
            .anyMatch(source -> source != null && source.language() == Language.PYTHON);
        if (!affectsPython) {
            return;
        }
        currentSources.values().stream()
            .filter(source -> source.language() == Language.PYTHON)
            .filter(source -> source.relativePath().endsWith("/__init__.py")
                || source.relativePath().equals("__init__.py"))
            .map(SourceState::key)
            .forEach(affected::add);
    }

    private static Set<String> changedSources(Map<String, SourceState> previous,
                                              Map<String, SourceState> current) {
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, SourceState> entry : current.entrySet()) {
            SourceState old = previous.get(entry.getKey());
            if (old == null || !old.hash().equals(entry.getValue().hash())) {
                changed.add(entry.getKey());
            }
        }
        for (String source : previous.keySet()) {
            if (!current.containsKey(source)) {
                changed.add(source);
            }
        }
        return changed;
    }

    private static Set<String> affectedSources(Set<String> changed,
                                               Map<String, SourceState> previous,
                                               Map<String, SourceState> current) {
        Map<String, Set<String>> dependents = new HashMap<>();
        Stream.concat(previous.values().stream(), current.values().stream()).forEach(source -> {
            for (String dependency : source.dependencies()) {
                dependents.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(source.key());
            }
        });
        Set<String> affected = new LinkedHashSet<>(changed);
        ArrayDeque<String> queue = new ArrayDeque<>(changed);
        while (!queue.isEmpty()) {
            String source = queue.removeFirst();
            for (String dependent : dependents.getOrDefault(source, Set.of())) {
                if (affected.add(dependent)) {
                    queue.addLast(dependent);
                }
            }
        }
        return affected;
    }

    private Map<String, ScannedSource> scanSources() {
        Map<String, ScannedSource> sources = new LinkedHashMap<>();
        if (javaRoot != null && Files.isDirectory(javaRoot)) {
            scanRoot(javaRoot, ".java", Language.JAVA, sources);
        }
        for (Path pythonRoot : pythonRoots) {
            if (Files.isDirectory(pythonRoot)) {
                scanRoot(pythonRoot, ".py", Language.PYTHON, sources);
            }
        }
        return sources;
    }

    private static void scanRoot(Path root,
                                 String extension,
                                 Language language,
                                 Map<String, ScannedSource> sources) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(extension))
                .sorted()
                .forEach(path -> {
                    try {
                        Path normalized = normalizeSourcePath(path);
                        String content = Files.readString(normalized);
                        String relative = root.relativize(normalized).toString().replace(File.separatorChar, '/');
                        Set<String> types = language == Language.JAVA
                            ? javaTypes(content)
                            : pythonTypes(content, relative);
                        SourceState state = new SourceState(
                            normalized.toString(),
                            language,
                            relative,
                            hash(content.getBytes(StandardCharsets.UTF_8)),
                            Set.of(),
                            Set.of(),
                            types
                        );
                        sources.put(state.key(), new ScannedSource(state, content));
                    } catch (IOException e) {
                        throw new SourceScanException(e);
                    }
                });
        } catch (SourceScanException e) {
            throw new PyronautCompilerException("Failed to read source: " + e.getCause().getMessage());
        } catch (IOException e) {
            throw new PyronautCompilerException("Failed to scan source directory " + root + ": " + e.getMessage());
        }
    }

    private static Map<String, SourceState> createSourceStates(Map<String, ScannedSource> sources) {
        Map<String, Set<String>> typeOwners = new HashMap<>();
        Map<String, String> pythonModules = new HashMap<>();
        for (ScannedSource source : sources.values()) {
            for (String type : source.state().types()) {
                typeOwners.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).add(source.state().key());
                int packageSeparator = type.lastIndexOf('.');
                String simpleName = packageSeparator == -1 ? type : type.substring(packageSeparator + 1);
                typeOwners.computeIfAbsent(simpleName, ignored -> new LinkedHashSet<>()).add(source.state().key());
            }
            if (source.state().language() == Language.PYTHON) {
                String module = pythonModule(source.state().relativePath());
                pythonModules.put(module, source.state().key());
            }
        }

        Map<String, SourceState> result = new LinkedHashMap<>();
        for (ScannedSource source : sources.values()) {
            Set<String> dependencies = new LinkedHashSet<>();
            Matcher identifiers = IDENTIFIER.matcher(source.content());
            while (identifiers.find()) {
                String identifier = identifiers.group();
                for (String owner : typeOwners.getOrDefault(identifier, Set.of())) {
                    if (!owner.equals(source.state().key())) {
                        dependencies.add(owner);
                    }
                }
            }
            if (source.state().language() == Language.PYTHON) {
                for (String importedModule : pythonImports(source.content())) {
                    String module = importedModule;
                    if (module.startsWith(".")) {
                        module = resolveRelativePythonModule(source.state().relativePath(), module);
                    }
                    String owner = pythonModules.get(module);
                    if (owner == null) {
                        owner = pythonModules.get(module + PYTHON_INIT_MODULE_SUFFIX);
                    }
                    if (owner != null && !owner.equals(source.state().key())) {
                        dependencies.add(owner);
                    }
                }
            }
            SourceState state = source.state();
            result.put(state.key(), new SourceState(
                state.key(),
                state.language(),
                state.relativePath(),
                state.hash(),
                Set.copyOf(dependencies),
                Set.of(),
                state.types()
            ));
        }
        return result;
    }

    private static String pythonModule(String relativePath) {
        return relativePath.substring(0, relativePath.length() - ".py".length()).replace('/', '.');
    }

    private static Set<String> javaTypes(String content) {
        String packageName = "";
        Matcher packageMatcher = JAVA_PACKAGE.matcher(content);
        if (packageMatcher.find()) {
            packageName = packageMatcher.group(1);
        }
        Set<String> types = new LinkedHashSet<>();
        Matcher typeMatcher = JAVA_TYPE.matcher(content);
        while (typeMatcher.find()) {
            String type = typeMatcher.group(1);
            types.add(packageName.isEmpty() ? type : packageName + '.' + type);
        }
        return Set.copyOf(types);
    }

    private static Set<String> pythonTypes(String content, String relativePath) {
        String packageName = pythonPackage(relativePath);
        Set<String> types = new LinkedHashSet<>();
        Matcher matcher = PYTHON_TYPE.matcher(content);
        while (matcher.find()) {
            types.add(packageName + '.' + matcher.group(1));
        }
        String fileName = Path.of(relativePath).getFileName().toString();
        String scriptName = fileName.substring(0, fileName.length() - ".py".length());
        types.add(packageName + '.' + capitalize(scriptName));
        return Set.copyOf(types);
    }

    private static String pythonPackage(String relativePath) {
        int separator = relativePath.lastIndexOf('/');
        if (separator == -1) {
            return "python";
        }
        return relativePath.substring(0, separator).replace('/', '.');
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Map<String, SourceState> assignOutputs(Map<String, SourceState> sources,
                                                   Set<String> outputs,
                                                   Map<String, Set<String>> trackedOutputs,
                                                   Set<String> trackedAggregatingOutputs,
                                                   Set<String> pythonProcessorOutputs,
                                                   Set<String> contractViolatingOutputs) {
        Map<String, Set<String>> assigned = new LinkedHashMap<>();
        sources.keySet().forEach(key -> assigned.put(key, new LinkedHashSet<>()));
        for (String output : outputs) {
            if (trackedAggregatingOutputs.contains(output)) {
                continue;
            }
            String owner = outputOwner(output, sources);
            if (owner != null) {
                assigned.get(owner).add(output);
            }
        }
        trackedOutputs.forEach((source, sourceOutputs) -> {
            Set<String> assignedOutputs = assigned.get(source);
            if (assignedOutputs != null) {
                sourceOutputs.stream()
                    .filter(outputs::contains)
                    .filter(Predicate.not(contractViolatingOutputs::contains))
                    .filter(Predicate.not(pythonProcessorOutputs::contains))
                    .filter(output -> !isAttributedToPythonSource(output, source, assigned, sources))
                    .forEach(assignedOutputs::add);
            }
        });
        Map<String, SourceState> result = new LinkedHashMap<>();
        sources.forEach((key, source) -> result.put(key, new SourceState(
            source.key(),
            source.language(),
            source.relativePath(),
            source.hash(),
            source.dependencies(),
            Set.copyOf(assigned.get(key)),
            source.types()
        )));
        return result;
    }

    private static boolean isAttributedToPythonSource(String output,
                                                      String trackedSource,
                                                      Map<String, Set<String>> assigned,
                                                      Map<String, SourceState> sources) {
        SourceState tracked = sources.get(trackedSource);
        if (tracked == null || tracked.language() != Language.JAVA) {
            return false;
        }
        return assigned.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(trackedSource))
            .filter(entry -> entry.getValue().contains(output))
            .map(entry -> sources.get(entry.getKey()))
            .anyMatch(source -> source != null && source.language() == Language.PYTHON);
    }

    private static String outputOwner(String output, Map<String, SourceState> sources) {
        if (output.startsWith(VFS_SOURCE_PREFIX)) {
            String relative = output.substring(VFS_SOURCE_PREFIX.length());
            for (SourceState source : sources.values()) {
                boolean isolatingPythonSource = source.language() == Language.PYTHON
                    && !source.relativePath().endsWith("/__init__.py")
                    && !source.relativePath().equals("__init__.py");
                if (isolatingPythonSource
                    && (relative.equals(source.relativePath()) || isPythonBytecode(relative, source.relativePath()))) {
                    return source.key();
                }
            }
            // Package initializers, launchers, indexes, and Java bridge modules are shared
            // Python runtime outputs and must not be attributed by their filename.
            return null;
        }
        String typePath = outputTypePath(output);
        if (typePath != null) {
            for (SourceState source : sources.values()) {
                for (String type : source.types()) {
                    if (typePath.equals(type.replace('.', '/'))) {
                        return source.key();
                    }
                }
            }
        }
        String fileName = Path.of(output).getFileName().toString();
        String owner = null;
        for (SourceState source : sources.values()) {
            if (matchesAnyType(fileName, source.types())) {
                if (owner != null && !owner.equals(source.key())) {
                    return null;
                }
                owner = source.key();
            }
        }
        return owner;
    }

    private static String outputTypePath(String output) {
        if (!output.endsWith(".class")) {
            return null;
        }
        String typePath = output.substring(0, output.length() - ".class".length());
        int nestedType = typePath.indexOf('$');
        return nestedType == -1 ? typePath : typePath.substring(0, nestedType);
    }

    private boolean isKnownCompilerSharedOutput(String output) {
        String generatedApplicationPath = packageName.replace('.', '/') + "/PyronautMain";
        return output.startsWith(VFS_ROOT + '/')
            || output.startsWith(generatedApplicationPath)
            || output.startsWith("META-INF/pyronaut/")
            || output.startsWith("META-INF/swagger/views/")
            || output.startsWith("META-INF/services/")
            || output.startsWith("META-INF/native-image/");
    }

    private static boolean isPythonBytecode(String output, String source) {
        int separator = source.lastIndexOf('/');
        String parent = separator == -1 ? "" : source.substring(0, separator + 1);
        String file = separator == -1 ? source : source.substring(separator + 1);
        String stem = file.substring(0, file.length() - ".py".length());
        return output.startsWith(parent + "__pycache__/" + stem + '.')
            && output.endsWith(".pyc");
    }

    private static boolean isBytecodeForDeclaredPythonOutput(String output,
                                                             Set<String> declaredPythonOutputs) {
        if (!output.startsWith(VFS_SOURCE_PREFIX) || !output.endsWith(".pyc")) {
            return false;
        }
        String relativeOutput = output.substring(VFS_SOURCE_PREFIX.length());
        return declaredPythonOutputs.stream()
            .filter(candidate -> candidate.startsWith(VFS_SOURCE_PREFIX) && candidate.endsWith(".py"))
            .map(candidate -> candidate.substring(VFS_SOURCE_PREFIX.length()))
            .anyMatch(source -> isPythonBytecode(relativeOutput, source));
    }

    private static boolean matchesAnyType(String fileName, Set<String> types) {
        for (String type : types) {
            int separator = type.lastIndexOf('.');
            String simpleName = separator == -1 ? type : type.substring(separator + 1);
            int index = fileName.indexOf(simpleName);
            while (index >= 0) {
                boolean before = index == 0 || isTypeBoundary(fileName.charAt(index - 1));
                int end = index + simpleName.length();
                boolean after = end == fileName.length()
                    || isTypeBoundary(fileName.charAt(end))
                    || fileName.startsWith("TargetTypeMapping", end);
                if (before && after) {
                    return true;
                }
                index = fileName.indexOf(simpleName, index + 1);
            }
        }
        return false;
    }

    private static boolean isTypeBoundary(char character) {
        return !Character.isLetterOrDigit(character) && character != '_';
    }

    private Set<String> listOutputs() throws IOException {
        if (!Files.isDirectory(targetDirectory)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.walk(targetDirectory)) {
            return paths.filter(Files::isRegularFile)
                .map(targetDirectory::relativize)
                .map(path -> path.toString().replace(File.separatorChar, '/'))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private boolean hasMissingOutputs(State state) {
        return Stream.of(
                state.sources().values().stream().flatMap(source -> source.outputs().stream()),
                state.aggregatingOutputs().stream(),
                state.pythonAggregatingOutputs().stream(),
                state.sharedOutputs().stream()
            )
            .flatMap(Stream::sequential)
            .map(targetDirectory::resolve)
            .anyMatch(Predicate.not(Files::isRegularFile));
    }

    private void rebuildPythonFilesList() throws IOException {
        Path vfsRoot = targetDirectory.resolve(VFS_ROOT);
        if (!Files.isDirectory(vfsRoot)) {
            return;
        }
        Path filesList = vfsRoot.resolve("fileslist.txt");
        List<String> entries;
        try (Stream<Path> paths = Files.walk(vfsRoot)) {
            entries = paths.filter(Files::isRegularFile)
                .filter(Predicate.not(filesList::equals))
                .map(targetDirectory::relativize)
                .map(path -> "/META-INF/" + path.toString().replace(File.separatorChar, '/')
                    .substring("META-INF/".length()))
                .sorted()
                .toList();
        }
        Files.createDirectories(filesList.getParent());
        Files.writeString(filesList, String.join(System.lineSeparator(), entries) + System.lineSeparator());
    }

    private void deleteManagedOutput(String output) throws IOException {
        Path resolved = targetDirectory.resolve(output).normalize();
        if (!resolved.startsWith(targetDirectory)) {
            throw new IOException("Incremental state contains an output outside the target directory: " + output);
        }
        Files.deleteIfExists(resolved);
        Path parent = resolved.getParent();
        while (parent != null && !parent.equals(targetDirectory)) {
            try {
                Files.delete(parent);
            } catch (IOException e) {
                break;
            }
            parent = parent.getParent();
        }
    }

    private State readState() {
        Path stateFile = cacheDirectory.resolve(STATE_FILE);
        if (!Files.isRegularFile(stateFile)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            properties.load(input);
            if (!STATE_VERSION.equals(properties.getProperty("version"))) {
                return null;
            }
            String fingerprint = properties.getProperty("global");
            if (fingerprint == null) {
                return null;
            }
            Map<String, SourceState> sources = new LinkedHashMap<>();
            for (String property : properties.stringPropertyNames()) {
                if (!property.startsWith(SOURCE_PREFIX) || !property.endsWith(".language")) {
                    continue;
                }
                String encodedKey = property.substring(SOURCE_PREFIX.length(), property.length() - ".language".length());
                String prefix = SOURCE_PREFIX + encodedKey + '.';
                String key = decode(encodedKey);
                SourceState state = new SourceState(
                    key,
                    Language.valueOf(properties.getProperty(prefix + "language")),
                    required(properties, prefix + "relative"),
                    required(properties, prefix + "hash"),
                    decodeList(properties.getProperty(prefix + "dependencies", "")),
                    decodeList(properties.getProperty(prefix + "outputs", "")),
                    decodeList(properties.getProperty(prefix + "types", ""))
                );
                sources.put(key, state);
            }
            State state = new State(
                fingerprint,
                Map.copyOf(sources),
                decodeList(properties.getProperty("aggregating.inputs", "")),
                decodeList(properties.getProperty("aggregating.outputs", "")),
                decodeList(properties.getProperty("python.aggregating.outputs", "")),
                decodeList(properties.getProperty("shared.outputs", "")),
                Boolean.parseBoolean(properties.getProperty("aggregating.processors", "false")),
                Boolean.parseBoolean(properties.getProperty("processor.compatible", "true"))
            );
            return isValidState(state) ? state : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidState(State state) {
        boolean validOutputs = Stream.of(
                state.sources().values().stream().flatMap(source -> source.outputs().stream()),
                state.aggregatingOutputs().stream(),
                state.pythonAggregatingOutputs().stream(),
                state.sharedOutputs().stream()
            )
            .flatMap(Stream::sequential)
            .allMatch(this::isManagedOutputPath);
        return validOutputs;
    }

    private boolean isManagedOutputPath(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(output);
            if (path.isAbsolute()) {
                return false;
            }
            Path resolved = targetDirectory.resolve(path).normalize();
            return !resolved.equals(targetDirectory) && resolved.startsWith(targetDirectory);
        } catch (Exception e) {
            return false;
        }
    }

    private void writeState(State state) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", STATE_VERSION);
        properties.setProperty("global", state.globalFingerprint());
        properties.setProperty("aggregating.inputs", encodeList(state.aggregatingInputs()));
        properties.setProperty("aggregating.outputs", encodeList(state.aggregatingOutputs()));
        properties.setProperty("python.aggregating.outputs", encodeList(state.pythonAggregatingOutputs()));
        properties.setProperty("shared.outputs", encodeList(state.sharedOutputs()));
        properties.setProperty("aggregating.processors", Boolean.toString(state.aggregatingProcessors()));
        properties.setProperty("processor.compatible", Boolean.toString(state.processorCompatible()));
        for (SourceState source : state.sources().values()) {
            String prefix = SOURCE_PREFIX + encode(source.key()) + '.';
            properties.setProperty(prefix + "language", source.language().name());
            properties.setProperty(prefix + "relative", source.relativePath());
            properties.setProperty(prefix + "hash", source.hash());
            properties.setProperty(prefix + "dependencies", encodeList(source.dependencies()));
            properties.setProperty(prefix + "outputs", encodeList(source.outputs()));
            properties.setProperty(prefix + "types", encodeList(source.types()));
        }
        Files.createDirectories(cacheDirectory);
        Path temporary = Files.createTempFile(cacheDirectory, "state-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Pyronaut incremental compilation state");
            }
            try {
                Files.move(temporary, cacheDirectory.resolve(STATE_FILE),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, cacheDirectory.resolve(STATE_FILE),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String fingerprintGlobalInputs() {
        MessageDigest digest = digest();
        update(digest, "state-version=" + STATE_VERSION);
        update(digest, "micronaut-version=" + VersionUtils.MICRONAUT_VERSION);
        update(digest, "java-root=" + javaRoot);
        pythonRoots.forEach(root -> update(digest, "python-root=" + root));
        update(digest, "target-directory=" + targetDirectory);
        update(digest, "python-code=" + pythonCode);
        update(digest, "package-name=" + packageName);
        update(digest, "application-class=" + applicationClass);
        update(digest, "python-bytecode=" + compilePythonBytecode);
        update(digest, "python-incremental-mode=" + pythonIncrementalMode);
        compilerOptions.forEach(option -> update(digest, "option=" + option));
        processorTypes.forEach(type -> update(digest, "processor=" + type));
        hashEntries(digest, "classpath", classpath);
        hashEntries(digest, "bootclasspath", bootclasspath);
        hashEntries(digest, "processor-path", annotationProcessorPath);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void hashEntries(MessageDigest digest, String group, List<File> entries) {
        update(digest, group);
        for (File entry : entries) {
            Path path = entry.toPath().toAbsolutePath().normalize();
            update(digest, path.toString());
            if (!Files.exists(path)) {
                update(digest, "missing");
            } else if (Files.isRegularFile(path)) {
                updateFile(digest, path);
            } else if (Files.isDirectory(path)) {
                try (Stream<Path> paths = Files.walk(path)) {
                    paths.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(file -> {
                            update(digest, path.relativize(file).toString());
                            updateFile(digest, file);
                        });
                } catch (IOException e) {
                    throw new PyronautCompilerException("Failed to fingerprint classpath directory " + path + ": " + e.getMessage());
                }
            }
        }
    }

    private static void updateFile(MessageDigest digest, Path file) {
        try {
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        } catch (IOException e) {
            throw new PyronautCompilerException("Failed to fingerprint " + file + ": " + e.getMessage());
        }
    }

    private void validateDirectories() {
        if (cacheDirectory.startsWith(targetDirectory) || targetDirectory.startsWith(cacheDirectory)) {
            throw new IllegalArgumentException(
                "incrementalCacheDirectory and targetDir must not contain one another"
            );
        }
    }

    private static List<Path> parseRoots(String roots) {
        if (roots == null || roots.isBlank()) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(roots, ",");
        while (tokenizer.hasMoreTokens()) {
            result.add(normalizeRoot(Path.of(tokenizer.nextToken().trim())));
        }
        return List.copyOf(result);
    }

    private static Path normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : normalizeRoot(Path.of(value));
    }

    private static Path normalizeRoot(Path path) {
        try {
            return path.toRealPath().normalize();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static Path normalizeSourcePath(Path path) throws IOException {
        return path.toRealPath().normalize();
    }

    private static List<File> copy(List<File> files) {
        return files == null ? List.of() : List.copyOf(files);
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String hash(byte[] content) {
        return HexFormat.of().formatHex(digest().digest(content));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String encodeList(Collection<String> values) {
        return values.stream().sorted().map(IncrementalCompilation::encode)
            .collect(java.util.stream.Collectors.joining(","));
    }

    private static Set<String> decodeList(String value) {
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        return Stream.of(value.split(","))
            .map(IncrementalCompilation::decode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    record Plan(boolean upToDate,
                boolean fullRebuild,
                Set<String> affectedSources,
                Set<String> isolatingAffectedSources,
                Map<String, SourceState> currentSources,
                State previous,
                boolean aggregating,
                Set<String> aggregatingInputs,
                boolean detectedAggregatingProcessors) {

        Set<Path> javaSources() {
            return affectedSources.stream()
                .map(currentSources::get)
                .filter(source -> source != null && source.language() == Language.JAVA)
                .map(SourceState::key)
                .map(Path::of)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        Set<String> pythonSources() {
            return isolatingAffectedSources.stream()
                .map(currentSources::get)
                .filter(source -> source != null && source.language() == Language.PYTHON)
                .map(SourceState::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        Set<String> currentPythonSources() {
            return currentSources.values().stream()
                .filter(source -> source.language() == Language.PYTHON)
                .map(SourceState::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        boolean processesAllPythonSources() {
            long pythonSourceCount = currentSources.values().stream()
                .filter(source -> source.language() == Language.PYTHON)
                .count();
            return pythonSources().size() == pythonSourceCount;
        }

        boolean needsCompilation(boolean generatedApplicationClass) {
            return !javaSources().isEmpty() || (generatedApplicationClass && affectsPythonSources());
        }

        boolean affectsPythonSources() {
            if (!pythonSources().isEmpty()) {
                return true;
            }
            if (previous == null) {
                return false;
            }
            return isolatingAffectedSources.stream()
                .map(previous.sources()::get)
                .anyMatch(source -> source != null && source.language() == Language.PYTHON);
        }

        boolean runsPythonProcessing() {
            return fullRebuild()
                || affectsPythonSources()
                || aggregatingAffectsPythonSources();
        }

        boolean aggregatingAffectsPythonSources() {
            if (!aggregating()) {
                return false;
            }
            return aggregatingInputs.stream()
                .map(currentSources::get)
                .anyMatch(source -> source != null && source.language() == Language.PYTHON);
        }
    }

    private record State(String globalFingerprint,
                         Map<String, SourceState> sources,
                         Set<String> aggregatingInputs,
                         Set<String> aggregatingOutputs,
                         Set<String> pythonAggregatingOutputs,
                         Set<String> sharedOutputs,
                         boolean aggregatingProcessors,
                         boolean processorCompatible) {
    }

    private record SourceState(String key,
                               Language language,
                               String relativePath,
                               String hash,
                               Set<String> dependencies,
                               Set<String> outputs,
                               Set<String> types) {
    }

    private record ScannedSource(SourceState state, String content) {
    }

    private enum Language {
        JAVA,
        PYTHON
    }

    private static final class SourceScanException extends RuntimeException {
        private SourceScanException(IOException cause) {
            super(cause);
        }
    }
}
