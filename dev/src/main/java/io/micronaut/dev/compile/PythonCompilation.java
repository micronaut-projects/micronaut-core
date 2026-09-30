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

import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.python.processing.PythonProcessingSession;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One Python module's compilations, for {@link PythonSourceCompiler}, which loads this class only
 * when {@code micronaut-inject-python} is present.
 *
 * <p>The Pyronaut compiler plans its incremental compilations itself: it tracks which Python and
 * Java sources changed, what their stubs depend on, and the classpath. It writes to its own
 * directory beside the class output, {@code <output>-python}, with its cache in
 * {@code <output>-python-cache}. After a compilation that succeeded, the files that differ are
 * copied into the class output, the generated sources into the generated sources directory, and those it no longer produces are removed, as one
 * {@link OutputTransaction}; the files copied are recorded in {@code <output>-python.outputs}, so
 * that a file another tool wrote into the class output is never removed, and its classes stay
 * resolvable by the sources through {@code <output>-python-foreign}. A failed compilation leaves
 * the class output as it was, and the Pyronaut compiler invalidates its own state, so the next one
 * compiles in full.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class PythonCompilation implements AutoCloseable {

    private static final String WORK_SUFFIX = "-python";
    private static final String CACHE_SUFFIX = "-python-cache";
    private static final String LEDGER_SUFFIX = "-python.outputs";
    private static final String FOREIGN_SUFFIX = "-python-foreign";
    private static final String STAGING_SUFFIX = "-python.micronaut-dev-staging";
    private static final String SOURCES_STAGING_SUFFIX = "-python-sources.micronaut-dev-staging";
    private static final String BACKUP_SUFFIX = "-python.micronaut-dev-backup";
    private static final String CLASS_SUFFIX = ".class";
    private static final String VFS_PREFIX = "META-INF/GRAALPY-VFS/";
    private static final String INDEX_PREFIX = "META-INF/micronaut/";
    private static final String APPLICATION_PACKAGE = "pyronaut_application/";

    private final PythonProcessingSession session = new PythonProcessingSession();

    CompilationResult compile(CompilationRequest request) {
        long start = System.nanoTime();
        Path classOutput = request.classOutput();
        Path work = sibling(classOutput, WORK_SUFFIX);
        Path cache = sibling(classOutput, CACHE_SUFFIX);
        List<String> pythonRoots = roots(request, SourceKind.PYTHON);
        List<String> javaRoots = roots(request, SourceKind.JAVA);
        List<CompileDiagnostic> diagnostics = new ArrayList<>();
        if (javaRoots.size() > 1) {
            diagnostics.add(error("A Python module is compiled with at most one Java source root, found " + javaRoots));
            return new CompilationResult(CompilationResult.Status.FAILED, diagnostics, Set.of(), Set.of(), elapsed(start));
        }
        Set<Path> compiled = new LinkedHashSet<>();
        try {
            if (request.isFull()) {
                OutputTransaction.deleteRecursively(work);
                OutputTransaction.deleteRecursively(cache);
            }
            PyronautCompiler.Builder builder = PyronautCompiler.builder()
                .targetDir(work.toFile())
                .incremental(true)
                .incrementalCacheDirectory(cache.toFile())
                .classpath(files(classpath(request, work, foreignClasses(classOutput))))
                .options(request.options())
                .pythonProcessingSession(session)
                .incrementalCompilationPlanCallback(plan -> compiled.addAll(plan.sources()));
            if (!pythonRoots.isEmpty()) {
                builder.pythonSrc(String.join(",", pythonRoots));
            }
            if (!javaRoots.isEmpty()) {
                builder.javaSrc(javaRoots.getFirst());
            }
            if (!request.processorPath().isEmpty()) {
                builder.annotationProcessorPath(files(request.processorPath()));
            }
            builder.build().compile();
        } catch (IOException | RuntimeException e) {
            diagnostics.add(error(e.getMessage() != null ? e.getMessage() : e.toString()));
            return new CompilationResult(CompilationResult.Status.FAILED, diagnostics, compiled, Set.of(), elapsed(start));
        }
        try {
            Mirrored mirrored = mirror(work, classOutput, request.generatedSources());
            CompilationResult.Status status = mirrored.changed().isEmpty() && mirrored.removed().isEmpty()
                ? CompilationResult.Status.NOTHING_TO_DO
                : CompilationResult.Status.SUCCESS;
            Set<String> classes = new LinkedHashSet<>(topLevelClasses(mirrored.changed()));
            classes.addAll(topLevelClasses(mirrored.removed()));
            return new CompilationResult(status, diagnostics, compiled, mirrored.removedOutputs(), elapsed(start), classes);
        } catch (IOException | UncheckedIOException e) {
            diagnostics.add(error("Cannot replace the outputs of the Python compiler: " + e.getMessage()));
            return new CompilationResult(CompilationResult.Status.FAILED, diagnostics, compiled, Set.of(), elapsed(start));
        }
    }

    @Override
    public void close() {
        session.close();
    }

    /**
     * Copies into the class output what the compiler produced and the class output does not hold as
     * it is, and removes what the previous compilations copied and this one did not produce.
     */
    private static Mirrored mirror(Path work, Path classOutput, Path generatedSources) throws IOException {
        Path ledger = sibling(classOutput, LEDGER_SUFFIX);
        Path staging = sibling(classOutput, STAGING_SUFFIX);
        Path sourcesStaging = sibling(classOutput, SOURCES_STAGING_SUFFIX);
        Path stagedLedger = ledger.resolveSibling(ledger.getFileName() + ".staged");
        Set<String> previous = owned(classOutput);
        Set<String> produced = relativeFiles(work);
        List<String> changed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        OutputTransaction.deleteRecursively(staging);
        OutputTransaction.deleteRecursively(sourcesStaging);
        try {
            for (String file : produced) {
                Path source = work.resolve(file);
                Path target = target(file, classOutput, generatedSources);
                if (!Files.isRegularFile(target) || Files.mismatch(source, target) != -1L) {
                    Path staged = (isSource(file) ? sourcesStaging : staging).resolve(file);
                    Files.createDirectories(staged.getParent());
                    Files.copy(source, staged);
                    changed.add(file);
                }
            }
            for (String file : previous) {
                if (!produced.contains(file) && Files.isRegularFile(target(file, classOutput, generatedSources))) {
                    removed.add(file);
                }
            }
            if (changed.isEmpty() && removed.isEmpty() && previous.equals(produced)) {
                return new Mirrored(List.of(), List.of(), Set.of());
            }
            Files.writeString(stagedLedger, String.join("\n", produced) + "\n", StandardCharsets.UTF_8);
            OutputTransaction transaction = new OutputTransaction(sibling(classOutput, BACKUP_SUFFIX));
            try {
                for (String file : removed) {
                    transaction.remove(target(file, classOutput, generatedSources));
                }
                transaction.promote(staging, classOutput);
                transaction.promote(sourcesStaging, generatedSources);
                transaction.put(stagedLedger, ledger);
                transaction.commit();
            } catch (IOException | UncheckedIOException e) {
                transaction.rollback();
                throw e;
            }
            Set<Path> removedOutputs = new LinkedHashSet<>(transaction.removed());
            removedOutputs.removeIf(Files::exists);
            return new Mirrored(changed, removed, removedOutputs);
        } finally {
            OutputTransaction.deleteRecursively(staging);
            OutputTransaction.deleteRecursively(sourcesStaging);
            Files.deleteIfExists(stagedLedger);
        }
    }

    /**
     * Where a file the compiler produced goes: a generated source, which the Pyronaut compiler writes beside
     * the classes, to the generated sources directory, anything else to the class output.
     */
    private static Path target(String file, Path classOutput, Path generatedSources) {
        return (isSource(file) ? generatedSources : classOutput).resolve(file);
    }

    private static boolean isSource(String file) {
        return file.endsWith(".java") && !file.startsWith("META-INF/");
    }

    /**
     * What the Pyronaut compiler produced in the class output: what the ledger recorded, or, before this
     * compiler ever wrote there, what a build with {@code pyronaut process} left, recognised by its shape.
     */
    private static Set<String> owned(Path classOutput) throws IOException {
        Path ledger = sibling(classOutput, LEDGER_SUFFIX);
        return Files.isRegularFile(ledger) ? readLedger(ledger) : builtOutputs(classOutput);
    }

    /**
     * The outputs of a build by the Pyronaut compiler: the Python modules under {@code META-INF/GRAALPY-VFS},
     * the generated application class, and the stubs, whose sources the compiler writes beside their classes,
     * with their classes, the classes generated for them and their index entries. A module deleted before
     * the first compilation in development mode takes these with it; the classes of a Java source deleted
     * then stay, as they do for the other compilers, until the build tool cleans them.
     */
    static Set<String> builtOutputs(Path classOutput) throws IOException {
        Set<String> files = relativeFiles(classOutput);
        Set<String> stubs = new LinkedHashSet<>();
        for (String file : files) {
            if (isSource(file)) {
                stubs.add(file.substring(0, file.length() - ".java".length()).replace('/', '.'));
            }
        }
        Set<String> built = new TreeSet<>();
        for (String file : files) {
            Set<String> classes = topLevelClasses(List.of(file));
            boolean stubClass = !classes.isEmpty() && stubs.containsAll(classes);
            if (file.startsWith(VFS_PREFIX) || file.startsWith(APPLICATION_PACKAGE) || isSource(file) || stubClass || isIndexEntryOf(file, stubs)) {
                built.add(file);
            }
        }
        return built;
    }

    private static boolean isIndexEntryOf(String file, Set<String> classes) {
        if (!file.startsWith(INDEX_PREFIX)) {
            return false;
        }
        String entry = file.substring(file.lastIndexOf('/') + 1);
        for (String className : classes) {
            if (StagedSourceCompiler.belongsTo(entry, className, "")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> readLedger(Path ledger) throws IOException {
        if (!Files.isRegularFile(ledger)) {
            return Set.of();
        }
        try (Stream<String> lines = Files.lines(ledger, StandardCharsets.UTF_8)) {
            return lines.map(String::strip).filter(line -> !line.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static Set<String> relativeFiles(Path directory) throws IOException {
        Set<String> files = new TreeSet<>();
        if (!Files.isDirectory(directory)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile).forEach(file -> files.add(directory.relativize(file).toString().replace(File.separatorChar, '/')));
        }
        return files;
    }

    /**
     * The top-level classes of class files, by binary name: {@code Foo} for {@code Foo$Inner.class}
     * and for the generated {@code $Foo$Definition.class}.
     */
    static Set<String> topLevelClasses(List<String> files) {
        Set<String> classes = new LinkedHashSet<>();
        for (String file : files) {
            if (!file.endsWith(CLASS_SUFFIX) || file.startsWith("META-INF/")) {
                continue;
            }
            String name = file.substring(0, file.length() - CLASS_SUFFIX.length());
            int slash = name.lastIndexOf('/');
            String packagePath = slash < 0 ? "" : name.substring(0, slash + 1);
            String simple = slash < 0 ? name : name.substring(slash + 1);
            if (simple.startsWith("$")) {
                simple = simple.substring(1);
            }
            int dollar = simple.indexOf('$');
            if (dollar > 0) {
                simple = simple.substring(0, dollar);
            }
            if (!simple.isEmpty() && !"module-info".equals(simple) && !"package-info".equals(simple)) {
                classes.add((packagePath + simple).replace('/', '.'));
            }
        }
        return classes;
    }

    private static List<String> roots(CompilationRequest request, SourceKind kind) {
        List<String> roots = new ArrayList<>();
        for (SourceRoot root : request.sourceRoots()) {
            if (root.kind() == kind && Files.isDirectory(root.path())) {
                roots.add(root.path().toAbsolutePath().normalize().toString());
            }
        }
        return roots;
    }

    /**
     * The compile classpath without the class output, whose classes of the previous generation must not
     * be resolved in place of the sources being compiled; the compiler adds its own output itself. What
     * another tool wrote into a shared class output is resolvable through the foreign classes instead.
     */
    private static List<Path> classpath(CompilationRequest request, Path work, @Nullable Path foreign) {
        Path classOutput = request.classOutput();
        List<Path> classpath = new ArrayList<>();
        for (Path entry : request.compileClasspath()) {
            Path normalized = entry.toAbsolutePath().normalize();
            if (!normalized.equals(classOutput) && !normalized.equals(work) && !normalized.equals(foreign)) {
                classpath.add(normalized);
            }
        }
        if (foreign != null) {
            classpath.add(foreign);
        }
        return classpath;
    }

    /**
     * The class files of the class output that this compiler did not write, as the files a previous
     * compilation copied are recorded: those another compiler, or the build tool, wrote into a shared
     * class output, which the Python and Java sources may reference. They are linked, or copied where
     * links are not supported, into a directory beside the class output, rebuilt for every compilation.
     *
     * @return The directory, null when the class output holds no such class
     */
    @Nullable
    private static Path foreignClasses(Path classOutput) throws IOException {
        Path foreign = sibling(classOutput, FOREIGN_SUFFIX);
        OutputTransaction.deleteRecursively(foreign);
        Set<String> owned = owned(classOutput);
        boolean any = false;
        for (String file : relativeFiles(classOutput)) {
            if (!file.endsWith(CLASS_SUFFIX) || owned.contains(file)) {
                continue;
            }
            Path source = classOutput.resolve(file);
            Path target = foreign.resolve(file);
            Files.createDirectories(target.getParent());
            try {
                Files.createLink(target, source);
            } catch (IOException | UnsupportedOperationException e) {
                Files.copy(source, target);
            }
            any = true;
        }
        return any ? foreign : null;
    }

    private static List<File> files(List<Path> paths) {
        List<File> files = new ArrayList<>(paths.size());
        for (Path path : paths) {
            files.add(path.toFile());
        }
        return files;
    }

    private static Path sibling(Path output, String suffix) {
        return output.resolveSibling(output.getFileName() + suffix);
    }

    private static CompileDiagnostic error(String message) {
        return new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, message, null, 0, 0);
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /**
     * What a compilation changed in the class output.
     *
     * @param changed The files copied, relative to the class output
     * @param removed The files removed, relative to the class output
     * @param removedOutputs The files removed, absolute
     */
    private record Mirrored(List<String> changed, List<String> removed, Set<Path> removedOutputs) {
    }
}
