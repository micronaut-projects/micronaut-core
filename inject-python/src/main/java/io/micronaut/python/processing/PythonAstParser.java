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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.compiler.PythonBytecodeCompiler;
import io.micronaut.python.processing.diagnostic.PythonDiagnostic;
import io.micronaut.python.processing.staticcompile.Ir;
import io.micronaut.python.processing.staticcompile.StaticCompilationConfiguration;
import io.micronaut.python.processing.staticcompile.StaticCompilationPlan;
import io.micronaut.python.processing.typecheck.TypeCheckConfiguration;
import io.micronaut.python.processing.util.PythonJavaTypes;
import io.micronaut.python.processing.util.PythonKeywords;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.ScriptDef;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Parses Python source files into the internal Python processing model.
 */
@Experimental
public final class PythonAstParser {
    /**
     * A system property naming a file: when set, the Truffle CPU sampler profiles the Python side
     * of the pipeline and writes a histogram of the Python functions to the file when the context
     * closes. A development aid for the benchmarks; off otherwise.
     */
    public static final String CPU_SAMPLER_PROPERTY = "micronaut.python.cpusampler";

    public static final String PYTHON = "python";
    public static final String INJECT_RESOURCES = "GRAALPY-VFS/io.micronaut/micronaut-inject-python";
    private static final Source COMPILE_RUNTIME_AST_SOURCE = Source.newBuilder(PYTHON, """
        import importlib.util as _mn_runtime_importlib_util
        import marshal as _mn_runtime_marshal
        import struct as _mn_runtime_struct

        def _mn_compile_runtime_ast(tree, source, filename):
            code = compile(tree, filename, 'exec')
            header = (
                _mn_runtime_importlib_util.MAGIC_NUMBER
                + _mn_runtime_struct.pack('<I', 0x03)
                + _mn_runtime_importlib_util.source_hash(source.encode('utf-8'))
            )
            return (
                _mn_runtime_importlib_util.cache_from_source(filename),
                header + _mn_runtime_marshal.dumps(code)
            )
        """, "micronaut-runtime-ast-compiler.py").cached(true).buildLiteral();
    // The driver snippets read their inputs from the context bindings, so one cached Source serves
    // every file: GraalPy parses a cached Source once per context instead of once per evaluation.
    private static final Source PROCESSOR_SOURCE = Source.newBuilder(PYTHON, getSource(), "micronaut-processor-driver.py").cached(true).buildLiteral();
    private static final Source CALL_EXTRACTION_SOURCE = Source.newBuilder(PYTHON, getCallExtractionSource(), "micronaut-call-extraction.py").cached(true).buildLiteral();
    private static final Source TRANSFORM_SOURCE = Source.newBuilder(PYTHON, getTransformSource(), "micronaut-transform-driver.py").cached(true).buildLiteral();
    // the caches the modules of one compilation share: made afresh per transform and per parse, since
    // the class elements they hold belong to one javac task
    private static final Source TRANSFORM_CACHES_SOURCE = Source.newBuilder(PYTHON, "_mn_transform_caches = {}", "micronaut-transform-caches.py").cached(true).buildLiteral();
    private static final Source PROCESSOR_CACHES_SOURCE = Source.newBuilder(PYTHON, "_mn_processor_caches = {}", "micronaut-processor-caches.py").cached(true).buildLiteral();
    private static final Source TYPE_CHECKER_SOURCE = Source.newBuilder(PYTHON, """
        if type_check_enabled:
            from micronaut_typecheck import TypeChecker
            type_checker = TypeChecker(type_check_mode, list(type_check_annotations))
        else:
            type_checker = None
        if static_compile_enabled:
            from micronaut_static import StaticPlanner
            static_planner = StaticPlanner(static_compile_mode, list(static_compile_annotations), static_compile_strict)
        else:
            static_planner = None
        """, "micronaut-typecheck-init.py").cached(true).buildLiteral();
    private static final Source TYPE_CHECK_SOURCE = Source.newBuilder(PYTHON, """
        diagnostics = [] if type_checker is None else type_checker.check(visitor_context)
        """, "micronaut-typecheck-driver.py").cached(true).buildLiteral();
    private static final Source DELEGATION_SOURCE = Source.newBuilder(PYTHON, """
        from micronaut_static import apply_delegation
        delegated = apply_delegation(runtime_tree, list(delegation_targets))
        """, "micronaut-static-delegation-driver.py").cached(true).buildLiteral();
    private static final Source STATIC_PLAN_SOURCE = Source.newBuilder(PYTHON, """
        static_decisions = [] if static_planner is None else static_planner.plan(type_checker, visitor_context)
        static_bodies = [] if static_planner is None else list(static_planner.bodies)
        static_diagnostics = [] if static_planner is None else list(static_planner.diagnostics)
        """, "micronaut-static-plan-driver.py").cached(true).buildLiteral();
    private final Context context;
    private final Value runtimeAstCompiler;
    private final IdentityHashMap<TransformResult, TransformArtifacts> transformArtifacts = new IdentityHashMap<>();

    public PythonAstParser() {
        this(PythonAstParser.class.getClassLoader());
    }

    PythonAstParser(ClassLoader classLoader) {
        this(classLoader, false);
    }

    PythonAstParser(ClassLoader classLoader, boolean incremental) {
        // Each parser owns its engine. A JVM-wide shared engine was tried to keep compiled code warm
        // across compilations, but an engine pins every context created on it until that context is
        // closed, and the optimizing runtime keeps compiled code per engine: the compile-time test
        // suite, which creates hundreds of parsers in one JVM, ran out of heap on GraalVM CE.
        long started = PipelineTimings.start();
        this.context = buildTolerantly(classLoader, incremental);
        context.initialize(PYTHON);
        context.eval(COMPILE_RUNTIME_AST_SOURCE);
        runtimeAstCompiler = context.getBindings(PYTHON).getMember("_mn_compile_runtime_ast");
        PipelineTimings.record(PipelineTimings.CONTEXT, started);
    }

    /**
     * The GraalPy context the processor sources run in; used by tests that execute Python-level unit tests.
     *
     * @return The context
     */
    Context context() {
        return context;
    }

    private static Context.Builder newContextBuilder(ClassLoader classLoader) {
        Context.Builder builder = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(INJECT_RESOURCES)
                .resourceLoadingClass(PythonAstParser.class)
                .build())
            // Future hardening should constrain host access to the required Micronaut API surface.
            .allowHostAccess(HostAccess.ALL)
            .hostClassLoader(classLoader)
            .allowHostClassLookup(name -> name.startsWith("io.micronaut"));
        String samplerOutput = System.getProperty(CPU_SAMPLER_PROPERTY);
        if (samplerOutput != null && !samplerOutput.isEmpty()) {
            builder.option("cpusampler", "true")
                .option("cpusampler.Output", "histogram")
                .option("cpusampler.OutputFile", samplerOutput);
        }
        return builder;
    }

    /**
     * Builds the context, retrying without the optimizing-runtime tuning options if the runtime does
     * not recognise them.
     *
     * @param contextBuilder The builder, already carrying the tuning options when incremental
     * @param incremental    Whether the tuning options were applied
     * @param classLoader    The host class loader, needed to rebuild from scratch
     * @return The context
     */
    private static Context buildTolerantly(ClassLoader classLoader, boolean incremental) {
        Context.Builder contextBuilder = newContextBuilder(classLoader);
        if (incremental) {
            // Incremental processing is a short-lived workload. Tune GraalPy for startup latency
            // and avoid paying for a core-count-based compiler thread pool.
            contextBuilder.allowExperimentalOptions(true)
                .option("engine.Mode", "latency")
                .option("engine.CompilerThreads", "1");
        }
        // Both of those options exist only on the optimizing Truffle runtime. On the fallback
        // runtime - any JVM without JVMCI, which includes stock OpenJDK and a GraalVM CE not started
        // with -XX:+EnableJVMCI - build() throws IllegalArgumentException and Pyronaut cannot compile
        // Python at all. Tuning is not worth failing the build over, so fall back without them.
        try {
            return contextBuilder.build();
        } catch (IllegalArgumentException e) {
            if (!incremental) {
                throw e;
            }
            return newContextBuilder(classLoader).build();
        }
    }

    PythonBytecodeCompiler bytecodeCompiler() {
        return new PythonBytecodeCompiler(context);
    }

    public PythonEnvironment parse(@Language("python") String sources) {
        return parse(sources, "");
    }

    public PythonEnvironment parse(@Language("python") String sources, String packageName) {
        return parse(sources, packageName, null);
    }

    public PythonEnvironment parse(@Language("python") String sources, String packageName, VisitorContext visitorContext) {
        return parse(sources, null, packageName, visitorContext);
    }

    private PythonEnvironment parse(CharSequence sources, @Nullable Value tree, String packageName, VisitorContext visitorContext) {
        context.eval(PROCESSOR_CACHES_SOURCE);
        Map<String, DecoratorDef> decorators = new LinkedHashMap<>();
        Map<String, ClassDef> classes = new LinkedHashMap<>();
        Map<String, ScriptDef> scripts = new LinkedHashMap<>();

        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("callback", (Function<Object, Object>) o -> {
            if (o instanceof ClassDef classDef) {
                String qualifiedName = resolveQualifiedName(packageName, classDef);
                classes.put(qualifiedName, classDef);
            } else if (o instanceof ScriptDef scriptDef) {
                String qualifiedName = resolveScriptQualifiedName(packageName, scriptDef);
                scripts.put(qualifiedName, scriptDef);
            } else if (o instanceof DecoratorDef decoratorDef) {
                decorators.put(decoratorDef.annotationName(), decoratorDef);
            }
            return o;
        });
        initializeTypeChecker(null, null);
        List<PythonDiagnostic> diagnostics = evaluateProcessor(bindings, sources, tree, packageName != null ? packageName : "", "Unknown", "Unknown", "", visitorContext);
        return new PythonEnvironment(
            classes,
            scripts,
            decorators,
            Map.of(),
            diagnostics,
            context
        );
    }

    /**
     * Runs the processor over one source. The processor visits the tree the transformer produced
     * when there is one, so every definition keeps the position it has in the original source; the
     * source text is still bound because the positions of the tree refer to it.
     *
     * @return The problems the processor found in the source
     */
    @SuppressWarnings("unchecked")
    private List<PythonDiagnostic> evaluateProcessor(Value bindings,
                                                     CharSequence sources,
                                                     @Nullable Value tree,
                                                     String packageName,
                                                     String fileName,
                                                     String sourcePath,
                                                     String srcRoot,
                                                     VisitorContext visitorContext) {
        bindings.putMember("src", sources);
        bindings.putMember("has_parsed_tree", tree != null);
        bindings.putMember("parsed_tree", tree != null ? tree : "");
        bindings.putMember("package_name", packageName);
        bindings.putMember("visitor_context", visitorContext);
        bindings.putMember("file_name", fileName);
        bindings.putMember("source_path", sourcePath);
        bindings.putMember("src_root", srcRoot);
        context.eval(PROCESSOR_SOURCE);
        Value diagnostics = bindings.getMember("diagnostics");
        return diagnostics == null ? List.of() : List.copyOf(diagnostics.as(List.class));
    }

    /**
     * The path a source is reported under: its path, or its name when it has none.
     *
     * @param source The source
     * @return The path
     */
    static String sourcePathOf(Source source) {
        if (source.getPath() != null) {
            return source.getPath();
        }
        String name = source.getName();
        return name == null || name.isBlank() ? "Unnamed" : name;
    }

    private static @NotNull String resolveQualifiedName(String packageName, ClassDef classDef) {
        return javaTypeName(packageName, classDef.name());
    }

    private static @NotNull String resolveScriptQualifiedName(String packageName, ScriptDef scriptDef) {
        return javaTypeName(packageName, scriptDef.name());
    }

    /**
     * The name of the Java type generated for a Python definition; unlike {@code packageName + "." + name}
     * a definition of the root package is not prefixed with a dot.
     */
    private static @NotNull String javaTypeName(String packageName, String simpleName) {
        return StringUtils.isEmpty(packageName) ? simpleName : packageName + "." + simpleName;
    }

    /**
     * Parse the given sources located within the given source directory.
     *
     * @param sources The sources
     * @param srcDir  The source directory
     * @return The parsed environment
     */
    public PythonEnvironment parse(List<Source> sources, String srcDir) {
        return parse(sources, List.of(srcDir), null);
    }

    /**
     * Parse the given sources located within the given source directory.
     *
     * @param sources The sources
     * @param srcDirs  The source directories
     * @param visitorContext The visitor context for constant resolution
     * @return The parsed environment
     */
    public PythonEnvironment parse(List<Source> sources, List<String> srcDirs, VisitorContext visitorContext) {
        initializeTypeChecker(null, null);
        return parseSources(sources.stream().map(source -> new ParsedSource(source, null)).toList(), srcDirs, visitorContext);
    }

    /**
     * Parse the transformed sources located within the given source directories. The processor
     * visits the trees produced by {@link #transform(VisitorContext, List, Source...)}, so the
     * definitions of the environment carry the positions they have in the original sources.
     *
     * @param transformed    The transformed sources, as returned by this parser
     * @param srcDirs        The source directories
     * @param visitorContext The visitor context for constant resolution
     * @return The parsed environment
     * @since 5.3.0
     */
    public PythonEnvironment parseTransformed(List<TransformResult> transformed, List<String> srcDirs, VisitorContext visitorContext) {
        return parseTransformed(transformed, srcDirs, visitorContext, TypeCheckConfiguration.OFF);
    }

    /**
     * Parse the transformed sources located within the given source directories, collecting the
     * definitions for the type checker as configured. The check itself runs once every source is
     * modelled, see {@link #typeCheck(VisitorContext)}.
     *
     * @param transformed    The transformed sources, as returned by this parser
     * @param srcDirs        The source directories
     * @param visitorContext The visitor context for constant resolution
     * @param typeCheck      The type checking requested for the compilation
     * @return The parsed environment
     * @since 5.3.0
     */
    public PythonEnvironment parseTransformed(List<TransformResult> transformed,
                                              List<String> srcDirs,
                                              VisitorContext visitorContext,
                                              TypeCheckConfiguration typeCheck) {
        return parseTransformed(transformed, srcDirs, visitorContext, typeCheck, StaticCompilationConfiguration.OFF);
    }

    /**
     * Parse the transformed sources located within the given source directories, collecting the
     * definitions for the type checker and the static compilation planner as configured. The check
     * runs once every source is modelled, see {@link #typeCheck(VisitorContext)}, and the plan after
     * it, see {@link #staticPlan(VisitorContext)}.
     *
     * @param transformed       The transformed sources, as returned by this parser
     * @param srcDirs           The source directories
     * @param visitorContext    The visitor context for constant resolution
     * @param typeCheck         The type checking requested for the compilation
     * @param staticCompilation The static compilation requested for the compilation
     * @return The parsed environment
     * @since 5.3.0
     */
    public PythonEnvironment parseTransformed(List<TransformResult> transformed,
                                              List<String> srcDirs,
                                              VisitorContext visitorContext,
                                              TypeCheckConfiguration typeCheck,
                                              StaticCompilationConfiguration staticCompilation) {
        long started = PipelineTimings.start();
        try {
            return parseTransformedTimed(transformed, srcDirs, visitorContext, typeCheck, staticCompilation);
        } finally {
            PipelineTimings.record(PipelineTimings.PARSE, started);
        }
    }

    private PythonEnvironment parseTransformedTimed(List<TransformResult> transformed,
                                                    List<String> srcDirs,
                                                    VisitorContext visitorContext,
                                                    TypeCheckConfiguration typeCheck,
                                                    StaticCompilationConfiguration staticCompilation) {
        List<ParsedSource> sources = new ArrayList<>(transformed.size());
        List<Source> originals = new ArrayList<>(transformed.size());
        for (TransformResult result : transformed) {
            sources.add(new ParsedSource(result.originalSource(), artifacts(result).tree()));
            originals.add(result.originalSource());
        }
        // the planner decides over the checker's records, so compiling anything needs the checker
        boolean planning = staticCompilation.isEnabledFor(originals);
        boolean checking = planning || typeCheck.isEnabledFor(originals);
        initializeTypeChecker(checking ? typeCheck : null, planning ? staticCompilation : null);
        return parseSources(sources, srcDirs, visitorContext);
    }

    /**
     * Creates the type checker the processor hands its definitions to and the planner deciding over
     * them, or removes them when nothing is checked or compiled so such a compilation costs nothing.
     */
    private void initializeTypeChecker(@Nullable TypeCheckConfiguration typeCheck, @Nullable StaticCompilationConfiguration staticCompilation) {
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("type_check_enabled", typeCheck != null);
        bindings.putMember("type_check_mode", typeCheck != null ? typeCheck.mode().optionValue() : "off");
        bindings.putMember("type_check_annotations", typeCheck != null ? typeCheck.annotationNames().toArray(String[]::new) : new String[0]);
        bindings.putMember("static_compile_enabled", staticCompilation != null);
        bindings.putMember("static_compile_mode", staticCompilation != null ? staticCompilation.mode().optionValue() : "off");
        bindings.putMember("static_compile_strict", staticCompilation != null && staticCompilation.strict());
        bindings.putMember("static_compile_annotations", staticCompilation != null ? staticCompilation.annotationNames().toArray(String[]::new) : new String[0]);
        context.eval(TYPE_CHECKER_SOURCE);
    }

    /**
     * Runs the type checker over the definitions collected by the last
     * {@link #parseTransformed(List, List, VisitorContext, TypeCheckConfiguration)}, once every
     * source of the compilation is modelled and its classes are registered with the given context.
     *
     * @param visitorContext The visitor context resolving the Java and Python classes of the compilation
     * @return The problems found, at the severity of the scope they were found in
     * @since 5.3.0
     */
    @SuppressWarnings("unchecked")
    public List<PythonDiagnostic> typeCheck(VisitorContext visitorContext) {
        Value bindings = context.getBindings(PYTHON);
        Value checker = bindings.getMember("type_checker");
        if (checker == null || checker.isNull()) {
            return List.of();
        }
        bindings.putMember("visitor_context", visitorContext);
        long started = PipelineTimings.start();
        context.eval(TYPE_CHECK_SOURCE);
        PipelineTimings.record(PipelineTimings.TYPE_CHECK, started);
        Value diagnostics = bindings.getMember("diagnostics");
        return diagnostics == null ? List.of() : List.copyOf(diagnostics.as(List.class));
    }

    /**
     * Runs the static compilation planner over the definitions collected by the last
     * {@link #parseTransformed(List, List, VisitorContext, TypeCheckConfiguration, StaticCompilationConfiguration)},
     * after {@link #typeCheck(VisitorContext)}, whose inference the planner decides on.
     *
     * @param visitorContext The visitor context resolving the Java and Python classes of the compilation
     * @return The plan: one decision per function, and the diagnostics for explicit switches not honoured
     * @since 5.3.0
     */
    @SuppressWarnings("unchecked")
    public StaticCompilationPlan staticPlan(VisitorContext visitorContext) {
        Value bindings = context.getBindings(PYTHON);
        Value planner = bindings.getMember("static_planner");
        if (planner == null || planner.isNull()) {
            return StaticCompilationPlan.EMPTY;
        }
        bindings.putMember("visitor_context", visitorContext);
        long started = PipelineTimings.start();
        context.eval(STATIC_PLAN_SOURCE);
        PipelineTimings.record(PipelineTimings.PLAN, started);
        Value decisions = bindings.getMember("static_decisions");
        Value bodies = bindings.getMember("static_bodies");
        Value diagnostics = bindings.getMember("static_diagnostics");
        return new StaticCompilationPlan(
            decisions == null ? List.of() : List.copyOf(decisions.as(List.class)),
            bodies == null ? Map.of() : StaticCompilationPlan.byKey(List.copyOf(bodies.as(List.class))),
            diagnostics == null ? List.of() : List.copyOf(diagnostics.as(List.class))
        );
    }

    private TransformArtifacts artifacts(TransformResult transformResult) {
        TransformArtifacts artifacts = transformArtifacts.get(transformResult);
        if (artifacts == null) {
            throw new IllegalArgumentException("Unknown Python transform result");
        }
        return artifacts;
    }

    private PythonEnvironment parseSources(List<ParsedSource> sources, List<String> srcDirs, VisitorContext visitorContext) {
        context.eval(PROCESSOR_CACHES_SOURCE);
        Map<String, DecoratorDef> decorators = new LinkedHashMap<>();
        Map<String, ClassDef> classes = new LinkedHashMap<>();
        Map<String, ScriptDef> scripts = new LinkedHashMap<>();

        // Every top-level class and every script generates a Java class, so two definitions of one
        // Java type name in different sources would silently overwrite each other; they are keyed by
        // that name here and resolved once all sources are parsed (see resolveDefinition)
        Map<String, List<Definition>> definitions = new LinkedHashMap<>();
        List<PythonDiagnostic> diagnostics = new ArrayList<>();
        String[] currentSource = new String[1];
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("callback", (Function<Object, Object>) o -> {
            if (o instanceof ClassDef classDef) {
                String typeName = javaTypeName(classDef.packageName(), classDef.name());
                definitions.computeIfAbsent(typeName, k -> new ArrayList<>()).add(new Definition(currentSource[0], classDef));
            } else if (o instanceof ScriptDef scriptDef) {
                String typeName = javaTypeName(scriptDef.packageName(), scriptDef.javaSimpleName());
                definitions.computeIfAbsent(typeName, k -> new ArrayList<>()).add(new Definition(currentSource[0], scriptDef));
            } else if (o instanceof DecoratorDef decoratorDef) {
                decorators.put(decoratorDef.annotationName(), decoratorDef);
            }
            return o;
        });

        for (ParsedSource parsedSource : sources) {
            Source source = parsedSource.source();
            String path = source.getPath();
            currentSource[0] = path != null ? path : source.getName();
            boolean processed = false;
            for (String srcDir : srcDirs) {
                if (path == null) {
                    String packageName = getPackageNameOfSource(srcDir, source);
                    String fileName = source.getName();
                    String effectiveName = fileName == null || fileName.isBlank() ? "Unnamed" : fileName;
                    diagnostics.addAll(evaluateProcessor(bindings, source.getCharacters(), parsedSource.tree(), packageName, effectiveName, effectiveName, srcDir, visitorContext));
                    processed = true;
                } else if (isWithinSourceDir(srcDir, path)) {
                    String packageName = getPackageNameOfSource(srcDir, source);
                    diagnostics.addAll(evaluateProcessor(bindings, source.getCharacters(), parsedSource.tree(), packageName, source.getName(), path, srcDir, visitorContext));
                    processed = true;
                }
            }
            if (!processed) {
                // Never skip a source quietly: its classes would be missing from the compiled application
                throw new ProcessingException(
                    null,
                    "Python source [" + currentSource[0] + "] is not located in any of the Python source directories " + srcDirs + " and cannot be processed"
                );
            }
        }
        Map<String, List<String>> shadowedTypes = new LinkedHashMap<>();
        definitions.forEach((typeName, candidates) -> {
            String winningSource = resolveDefinition(typeName, candidates, decorators, visitorContext).source();
            for (Definition candidate : candidates) {
                // a module and its class of one name (implementation.py defining Implementation) are both kept
                if (!candidate.source().equals(winningSource)) {
                    if (candidate.element() instanceof ClassDef classDef) {
                        // the package initializer imports the winner only, so the runtime resolves the same definition
                        shadowedTypes.computeIfAbsent(candidate.source(), k -> new ArrayList<>()).add(classDef.name());
                    }
                } else if (candidate.element() instanceof ClassDef classDef) {
                    classes.put(resolveQualifiedName(classDef.packageName(), classDef), classDef);
                } else if (candidate.element() instanceof ScriptDef scriptDef) {
                    scripts.put(resolveScriptQualifiedName(scriptDef.packageName(), scriptDef), scriptDef);
                }
            }
        });
        return new PythonEnvironment(
            classes,
            scripts,
            decorators,
            shadowedTypes,
            diagnostics,
            context
        );
    }

    /**
     * Pick the source a generated Java type is built from. Only a definition that yields a bean,
     * an introspection or another generated member (a class carrying Java annotations, a module with
     * functions or decorators) conflicts with another such definition of the same name; a plain
     * module-private class ({@code Helper}, {@code Config}) may be defined in several modules of a
     * package, in which case the annotated definition, or else the last one, provides the Java stub.
     */
    private static Definition resolveDefinition(String typeName, List<Definition> candidates, Map<String, DecoratorDef> decorators, VisitorContext visitorContext) {
        Definition winner = candidates.get(0);
        if (candidates.size() == 1) {
            return winner;
        }
        boolean winnerSignificant = winner.isSignificant(decorators, visitorContext);
        for (Definition candidate : candidates.subList(1, candidates.size())) {
            boolean significant = candidate.isSignificant(decorators, visitorContext);
            if (winner.conflictsWith(winnerSignificant, candidate, significant)) {
                throw new ProcessingException(
                    null,
                    "Duplicate Python type [" + typeName + "] defined in [" + candidate.source() + "] and [" + winner.source() + "]: "
                        + "an annotated top-level class or a module with functions generates a Java class named after it, so the two "
                        + "definitions would overwrite each other; rename one of them or move it to another package"
                );
            }
            boolean replace = significant || !winnerSignificant;
            if (!winner.source().equals(candidate.source())) {
                visitorContext.info("Python type [" + typeName + "] is defined in [" + winner.source() + "] and [" + candidate.source()
                    + "]; the generated Java type follows [" + (replace ? candidate : winner).source() + "]", null);
            }
            if (replace) {
                winner = candidate;
                winnerSignificant = significant;
            }
        }
        return winner;
    }

    static boolean isWithinSourceDir(String srcDir, String path) {
        String normalizedSrcDir = normalizePath(srcDir);
        String normalizedPath = normalizePath(path);
        return normalizedPath.startsWith(normalizedSrcDir) || normalizedPath.startsWith("/private" + normalizedSrcDir);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        return normalized.length() > 2 && normalized.charAt(0) == '/' && Character.isLetter(normalized.charAt(1)) && normalized.charAt(2) == ':'
            ? normalized.substring(1)
            : normalized;
    }

    private static String sourceRootOf(List<String> srcDirs, Source source) {
        String path = source.getPath();
        if (path != null) {
            for (String srcDir : srcDirs) {
                if (isWithinSourceDir(srcDir, path)) {
                    return srcDir;
                }
            }
        }
        return "";
    }

    public static String getPackageNameOfSource(String srcDir, Source source) {
        String path = normalizePath(source.getPath());
        srcDir = normalizePath(srcDir);
        String packageName = "python";
        if (StringUtils.isNotEmpty(srcDir) && StringUtils.isNotEmpty(path)) {
            int i = path.indexOf(srcDir);
            if (i > -1) {
                packageName = path.substring(i + srcDir.length() + 1);
            }

            if (packageName.indexOf('/') > -1) {
                String fileName = "/" + source.getName();
                if (packageName.endsWith(fileName)) {
                    packageName = packageName.substring(0, packageName.length() - fileName.length());
                }
                packageName = packageName.replace('/', '.');
            } else {
                return "python";
            }
        }
        return packageName;
    }

    public TransformResult transform(VisitorContext visitorContext, @Language("python") String sources) {
        Source pythonSource = Source.create(PYTHON, sources);
        return transform(visitorContext, pythonSource).get(0);
    }

    /**
     * Extracts call expressions from source without evaluating user code.
     *
     * @param source the Python source
     * @return discovered calls
     */
    /**
     * Extracts call metadata from Python source without executing it.
     * @param source Python source
     * @return discovered calls
     */
    public List<PythonCall> extractCalls(Source source) {
        List<PythonCall> calls = new ArrayList<>();
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("build_call_callback", (Function<Object, Object>) value -> {
            if (value instanceof PythonCall call) {
                calls.add(call);
            }
            return value;
        });
        bindings.putMember("src", source.getCharacters());
        context.eval(CALL_EXTRACTION_SOURCE);
        return calls;
    }

    /**
     * The run time strips a leading {@code io.} from every Java package when it derives the Python
     * module name, so {@code io.swagger.v3.oas.annotations} is imported as
     * {@code swagger.v3.oas.annotations}. Compile-time lookups therefore have to try the prefixed
     * name as well, for any library and not only {@code io.micronaut}.
     */
    private static final String JAVA_IO_PACKAGE_PREFIX = "io.";

    private static boolean isJavaIoPackage(String name) {
        return name.startsWith(JAVA_IO_PACKAGE_PREFIX);
    }

    public @NotNull List<TransformResult> transform(VisitorContext visitorContext, Source... pythonSource) {
        return transform(visitorContext, List.of(), pythonSource);
    }

    /**
     * Transforms the given sources located within the given source directories. A source of a source
     * directory is transformed with its package known, so the transformer can resolve the imports of
     * sibling modules of that directory; a module found in one of the directories is a Python module,
     * never a Java import that has to resolve on the compile classpath.
     *
     * @param visitorContext The visitor context
     * @param srcDirs The source directories
     * @param pythonSource The sources
     * @return The transformed sources, in the order of the sources
     */
    public @NotNull List<TransformResult> transform(VisitorContext visitorContext, List<String> srcDirs, Source... pythonSource) {
        transformArtifacts.clear();
        Value bindings = context.getBindings(PYTHON);
        context.eval(TRANSFORM_CACHES_SOURCE);
        bindings.putMember("python_source_dirs", srcDirs.toArray(String[]::new));
        Map<String, ClassElement> classElementCache = new LinkedHashMap<>();
        Set<String> missingClassElements = new java.util.HashSet<>();
        Map<String, Object[]> packageClassElementsCache = new LinkedHashMap<>();
        bindings.putMember("callback_get_class_element", (Function<String, Object>) name -> {
            // Transform package names back from "micronaut." to "io.micronaut." for Java lookups
            name = normalizeKeywordSafePackageName(name);
            String javaName = name.startsWith("micronaut.") ? "io." + name : name;
            ClassElement cachedClassElement = classElementCache.get(javaName);
            if (cachedClassElement != null) {
                return cachedClassElement;
            }
            if (missingClassElements.contains(javaName)) {
                return null;
            }
            var classElement = visitorContext.getClassElement(javaName);
            if (classElement.isEmpty() && !javaName.equals(name)) {
                // a class generated from a Python source of a package under micronaut.* carries no io. prefix
                classElement = visitorContext.getClassElement(name).filter(PythonJavaTypes::isPythonClass);
            }
            if (classElement.isEmpty() && !isJavaIoPackage(javaName)) {
                // The run time strips a leading "io." from every Java package, not just io.micronaut,
                // so an import written the way the run time names it -- swagger.v3.oas.annotations for
                // io.swagger.v3.oas.annotations -- has to resolve here too.
                classElement = visitorContext.getClassElement(JAVA_IO_PACKAGE_PREFIX + javaName);
            }
            if (classElement.isPresent()) {
                classElementCache.put(javaName, classElement.get());
                return classElement.get();
            }
            missingClassElements.add(javaName);
            return null;
        });
        bindings.putMember("callback_get_class_elements", (Function<String, Object[]>) packageName -> {
            // Transform package names back from "micronaut." to "io.micronaut." for Java lookups
            packageName = normalizeKeywordSafePackageName(packageName);
            String javaPackageName = packageName.startsWith("micronaut.") ? "io." + packageName : packageName;
            return packageClassElementsCache.computeIfAbsent(
                javaPackageName,
                name -> {
                    Object[] elements = visitorContext.getClassElements(name, "*");
                    if ((elements == null || elements.length == 0) && !isJavaIoPackage(name)) {
                        // As above: io.swagger.v3.oas.annotations is imported as swagger.v3.oas.annotations.
                        elements = visitorContext.getClassElements(JAVA_IO_PACKAGE_PREFIX + name, "*");
                    }
                    return elements;
                }
            );
        });
        List<TransformResult> results = new ArrayList<>();
        for (Source source : pythonSource) {
            bindings.putMember("src", source.getCharacters());
            bindings.putMember("source_path", sourcePathOf(source));
            String sourceRoot = sourceRootOf(srcDirs, source);
            bindings.putMember("source_root", sourceRoot);
            bindings.putMember("package_name", sourceRoot.isEmpty() ? "" : getPackageNameOfSource(sourceRoot, source));

            Value result;
            long started = PipelineTimings.start();
            try {
                result = context.eval(TRANSFORM_SOURCE);
                PipelineTimings.record(PipelineTimings.TRANSFORM, started);
            } catch (Exception e) {
                StringWriter stack = new StringWriter();
                e.printStackTrace(new PrintWriter(stack));
                throw new ProcessingException(null, "Error processing Python source [" + source.getName() + "]: " + e.getMessage() + System.lineSeparator() + stack, e);
            }
            Map map = result.as(Map.class);
            // The transformed source is only read by tests and error reports; the processor visits the
            // tree itself, so the text is rendered on demand instead of costing an unparse per file.
            Value codeFactory = result.getHashValue("code");
            Supplier<String> code = SupplierUtil.memoized(() -> codeFactory.execute().asString());
            Value runtimeCodeFactory = result.getHashValue("runtimeCode");
            Supplier<String> runtimeCode = runtimeCodeFactory != null && runtimeCodeFactory.canExecute()
                ? SupplierUtil.memoized(() -> runtimeCodeFactory.execute().asString())
                : code;
            Map<String, String> decorators = map.containsKey("decorators") ? (Map<String, String>) map.get("decorators") : null;
            Map<String, java.util.List<Map<String, String>>> javaClassImports =
                map.containsKey("javaClassImports") ? (Map<String, java.util.List<Map<String, String>>>) map.get("javaClassImports") : null;
            java.util.List<String> exportedTypes = map.containsKey("exportedTypes") ? (java.util.List<String>) map.get("exportedTypes") : new ArrayList<>();
            java.util.List<String> allClassNames = map.containsKey("allClassNames") ? (java.util.List<String>) map.get("allClassNames") : new ArrayList<>();
            java.util.List<PythonDiagnostic> validationErrors = map.containsKey("validationErrors") ? (java.util.List<PythonDiagnostic>) map.get("validationErrors") : new ArrayList<>();
            TransformResult transformResult = new TransformResult(
                source,
                code,
                runtimeCode,
                decorators,
                javaClassImports,
                exportedTypes,
                allClassNames,
                validationErrors
            );
            results.add(transformResult);
            transformArtifacts.put(
                transformResult,
                new TransformArtifacts(
                    result.getHashValue("tree"),
                    result.getHashValue("runtimeTree"),
                    result.getHashValue("runtimeRequired").asBoolean()
                )
            );
        }
        return results;
    }

    /**
     * Rewrites the compiled functions of the runtime tree of a source to delegate to their Java
     * bodies when the Python object is bound to its stub, keeping the original body as the
     * fallback for objects created in Python. A rewritten tree needs runtime bytecode.
     *
     * @param transformResult The transformed source
     * @param plan            The static compilation plan
     * @return How many functions were rewritten
     * @since 5.3.0
     */
    public int applyStaticDelegation(TransformResult transformResult, StaticCompilationPlan plan) {
        TransformArtifacts artifacts = artifacts(transformResult);
        String sourcePath = sourcePathOf(transformResult.originalSource());
        List<String> targets = new ArrayList<>();
        for (Ir.CompiledBody body : plan.bodies().values()) {
            // the bodies declared in this source: a class of the same simple name in another package has its own
            if (body.span() != null && !body.span().path().equals(sourcePath)) {
                continue;
            }
            // a collection the Java body returns becomes a Python one for a Python caller
            String conversion = switch (body.returnType()) {
                case "java.util.List" -> "#list";
                case "java.util.Set" -> "#set";
                case "java.util.Map" -> "#dict";
                default -> "";
            };
            targets.add(body.className().substring(body.className().lastIndexOf('.') + 1) + "#" + body.methodName() + conversion);
        }
        if (targets.isEmpty()) {
            return 0;
        }
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("runtime_tree", artifacts.runtimeTree());
        bindings.putMember("delegation_targets", targets.toArray(String[]::new));
        context.eval(DELEGATION_SOURCE);
        Value delegated = bindings.getMember("delegated");
        int count = delegated == null ? 0 : delegated.asInt();
        if (count > 0 && !artifacts.runtimeRequired()) {
            transformArtifacts.put(transformResult, new TransformArtifacts(artifacts.tree(), artifacts.runtimeTree(), true));
        }
        return count;
    }

    PythonBytecodeCompiler.Result compileRuntimeBytecode(TransformResult transformResult,
                                                         String filename) {
        TransformArtifacts artifacts = artifacts(transformResult);
        Value result = runtimeAstCompiler.execute(
            artifacts.runtimeTree(),
            transformResult.originalSource().getCharacters().toString(),
            filename
        );
        return new PythonBytecodeCompiler.Result(
            result.getArrayElement(0).asString(),
            result.getArrayElement(1).as(byte[].class)
        );
    }

    boolean requiresRuntimeBytecode(TransformResult transformResult) {
        TransformArtifacts artifacts = transformArtifacts.get(transformResult);
        return artifacts != null && artifacts.runtimeRequired();
    }

    private static String normalizeKeywordSafePackageName(String name) {
        return PythonKeywords.toJavaDottedName(name);
    }

    public PythonEnvironment process(@Language("python") String sources, VisitorContext visitorContext) {
        return process(sources, "", visitorContext);
    }

    public PythonEnvironment process(@Language("python") String sources, String packageName, VisitorContext visitorContext) {
        // First transform the code
        TransformResult transformedCode = transform(visitorContext, sources);

        // Then process the transformed tree
        return parse(sources, artifacts(transformedCode).tree(), packageName, null);
    }

    private static @Language("python") String getSource() {
        return """
            import ast
            import java
            from micronaut_processor import MicronautAstVisitor

            tree = parsed_tree if has_parsed_tree else ast.parse(src)
            visitor = MicronautAstVisitor(
                callback, package_name, file_name, visitor_context, src_root,
                source_path=source_path, source_text=src, type_checker=type_checker, caches=_mn_processor_caches
            )
            visitor.visit(tree)
            diagnostics = visitor.diagnostics
            """;
    }

    private static @Language("python") String getTransformSource() {
        return """
            import ast
            from micronaut_transformer import MicronautRuntimeTransformer, MicronautTransformer, ast_equal, unparse

            tree = ast.parse(src)
            transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements, False, package_name, source_root, python_source_dirs=python_source_dirs, source_path=source_path, source_text=src, caches=_mn_transform_caches)
            transformed_tree = transformer.visit(tree)
            # The diagnostic runtime source is only read by tests and error reports, so it is
            # produced on demand instead of costing a parse, a transformer pass and an unparse per file.
            def diagnostic_runtime_code(source=src, package_name=package_name, source_root=source_root):
                diagnostic_runtime_transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements, True, package_name, source_root)
                return unparse(diagnostic_runtime_transformer.visit(ast.parse(source)))
            executable_runtime_tree = ast.parse(src)
            # Transformers mutate in place, so a pristine parse (cheaper than a deep copy) is kept for
            # the change check; ast_equal stops at the first difference instead of serialising both trees.
            pristine_runtime_tree = ast.parse(src)
            missing_decorator_code = transformer.get_missing_runtime_decorator_code(executable_runtime_tree)
            runtime_transformer = MicronautRuntimeTransformer(
                callback_get_class_element,
                callback_get_class_elements,
                missing_decorator_code,
                package_name,
                source_root
            )
            transformed_runtime_tree = runtime_transformer.visit(executable_runtime_tree)
            ast.fix_missing_locations(transformed_runtime_tree)
            {
                "tree": transformed_tree,
                "code": lambda tree=transformed_tree: unparse(tree),
                "runtimeCode": diagnostic_runtime_code,
                "runtimeTree": transformed_runtime_tree,
                "runtimeRequired": not ast_equal(pristine_runtime_tree, transformed_runtime_tree),
                "decorators": transformer.get_generated_decorator_code(),
                "javaClassImports": transformer.get_java_class_imports(),
                "exportedTypes": transformer.get_exported_types(),
                "allClassNames": transformer.all_class_names,
                "validationErrors": transformer.validation_errors
            }
            """;
    }

    private static @Language("python") String getCallExtractionSource() {
        return """
            import ast
            import java
            PythonCall = java.type("io.micronaut.python.processing.PythonCall")

            def value(node):
                try:
                    return str(ast.literal_eval(node))
                except Exception:
                    return ast.unparse(node)

            class CallCollector(ast.NodeVisitor):
                def visit_Call(self, node):
                    name = node.func.id if isinstance(node.func, ast.Name) else node.func.attr if isinstance(node.func, ast.Attribute) else ""
                    arguments = [value(argument) for argument in node.args]
                    keywords = {keyword.arg: value(keyword.value) for keyword in node.keywords if keyword.arg is not None}
                    build_call_callback(PythonCall(name, arguments, keywords))
                    self.generic_visit(node)

            CallCollector().visit(ast.parse(src))
            """;
    }

    public void close() {
        transformArtifacts.clear();
        this.context.close();
    }

    /**
     * The trees a transformation produced.
     *
     * @param tree            The compile-time tree the processor visits
     * @param runtimeTree     The runtime tree the bytecode is compiled from
     * @param runtimeRequired Whether the runtime tree differs from the source
     */
    private record TransformArtifacts(Value tree, Value runtimeTree, boolean runtimeRequired) {
    }

    /**
     * A source to process.
     *
     * @param source The source
     * @param tree   The tree the transformer produced for it, when there is one
     */
    private record ParsedSource(Source source, @Nullable Value tree) {
    }

    /**
     * A source definition of a generated Java type.
     *
     * @param source  The defining source
     * @param element The class or script definition
     */
    private record Definition(String source, Object element) {

        /**
         * Whether the definition generates beans, introspections or bridged members: a class annotated
         * with a Java annotation or an annotation defined by the application, or a module with
         * functions or decorators. A module of assignments only and a plain class yield nothing that
         * another definition of the same name could not replace.
         */
        boolean isSignificant(Map<String, DecoratorDef> decorators, VisitorContext visitorContext) {
            if (element instanceof ScriptDef scriptDef) {
                return !scriptDef.functions().isEmpty() || !scriptDef.decorators().isEmpty();
            }
            ClassDef classDef = (ClassDef) element;
            return classDef.decorators().stream().anyMatch(decorator -> {
                String annotationName = decorator.annotationName();
                return decorators.containsKey(annotationName) || visitorContext.getClassElement(annotationName).isPresent();
            });
        }

        /**
         * Whether two definitions of one name overwrite each other: both are significant, or a
         * module with functions meets a class, whose stub would replace the module's Java class
         * regardless of its annotations.
         */
        boolean conflictsWith(boolean significant, Definition other, boolean otherSignificant) {
            if (source.equals(other.source)) {
                return false;
            }
            if (significant && otherSignificant) {
                return true;
            }
            boolean script = element instanceof ScriptDef;
            boolean otherScript = other.element instanceof ScriptDef;
            return script != otherScript && (script ? significant : otherSignificant);
        }
    }

    /**
     * The result of a transformation.
     *
     * @param originalSource   The original source
     * @param codeSupplier     Produces the transformed code for tests and diagnostics on demand
     * @param runtimeCodeSupplier Produces the runtime code for diagnostics on demand
     * @param decorators       The decorators
     * @param javaClassImports The Java class imports
     * @param exportedTypes    The types that have Micronaut decorators
     * @param allClassNames    All class names defined in the source
     * @param validationErrors The problems found while transforming the source, located in it
     */
    @Experimental
    public record TransformResult(
        Source originalSource,
        Supplier<String> codeSupplier,
        Supplier<String> runtimeCodeSupplier,
        Map<String, String> decorators,
        Map<String, java.util.List<Map<String, String>>> javaClassImports,
        java.util.List<String> exportedTypes,
        java.util.List<String> allClassNames,
        java.util.List<PythonDiagnostic> validationErrors) {

        /**
         * Creates a result whose transformed code is already rendered.
         *
         * @param originalSource      The original source
         * @param code                The transformed code
         * @param runtimeCodeSupplier Produces the runtime code for diagnostics on demand
         * @param decorators          The decorators
         * @param javaClassImports    The Java class imports
         * @param exportedTypes       The types that have Micronaut decorators
         * @param allClassNames       All class names defined in the source
         * @param validationErrors    The problems found while transforming the source, located in it
         */
        @SuppressWarnings("checkstyle:ParameterNumber")
        public TransformResult(Source originalSource,
                               String code,
                               Supplier<String> runtimeCodeSupplier,
                               Map<String, String> decorators,
                               Map<String, java.util.List<Map<String, String>>> javaClassImports,
                               java.util.List<String> exportedTypes,
                               java.util.List<String> allClassNames,
                               java.util.List<PythonDiagnostic> validationErrors) {
            this(originalSource, () -> code, runtimeCodeSupplier, decorators, javaClassImports, exportedTypes, allClassNames, validationErrors);
        }

        /**
         * The transformed source rendered as text. It is computed on first access; the processor
         * itself visits the transformed tree.
         *
         * @return The transformed code
         */
        public String code() {
            return codeSupplier.get();
        }

        public Source transformedSource() {
            return sourceWithContent(code());
        }

        /**
         * The runtime source rendered for diagnostics. It is computed on first access.
         *
         * @return The runtime code
         */
        public String runtimeCode() {
            return runtimeCodeSupplier.get();
        }

        public Source runtimeSource() {
            return sourceWithContent(runtimeCode());
        }

        private Source sourceWithContent(String content) {
            try {
                Source.Builder builder;
                if (originalSource.getURL() != null) {
                    builder = Source.newBuilder(originalSource.getLanguage(), originalSource.getURL());
                } else if (originalSource.getURI() != null && !originalSource.getURI().toString().startsWith("truffle:")) {
                    builder = Source.newBuilder(originalSource.getLanguage(), originalSource.getURI().toURL())
                        .uri(originalSource.getURI());
                } else {
                    builder = Source.newBuilder(originalSource.getLanguage(), originalSource.getCharacters(), originalSource.getName());
                }
                return builder
                    .name(originalSource.getName())
                    .content(content)
                    .build();
            } catch (IOException e) {
                throw new ProcessingException(null, "Unable to create transformed source for " + originalSource, e);
            }
        }
    }
}
