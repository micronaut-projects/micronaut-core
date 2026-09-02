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
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.compiler.PythonBytecodeCompiler;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import io.micronaut.python.processing.visitor.ScriptDef;
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

/**
 * Parses Python source files into the internal Python processing model.
 */
@Experimental
public final class PythonAstParser {

    public static final String PYTHON = "python";
    public static final String INJECT_RESOURCES = "GRAALPY-VFS/io.micronaut/micronaut-inject-python";
    private static final Set<String> PYTHON_KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    );
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
    private final Context context;
    private final Value runtimeAstCompiler;
    private final IdentityHashMap<TransformResult, RuntimeArtifact> runtimeArtifacts = new IdentityHashMap<>();

    public PythonAstParser() {
        this(PythonAstParser.class.getClassLoader());
    }

    PythonAstParser(ClassLoader classLoader) {
        this(classLoader, false);
    }

    PythonAstParser(ClassLoader classLoader, boolean incremental) {
        var contextBuilder = newContextBuilder(classLoader);
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
        this.context = buildTolerantly(contextBuilder, incremental, classLoader);
        context.initialize(PYTHON);
        context.eval(COMPILE_RUNTIME_AST_SOURCE);
        runtimeAstCompiler = context.getBindings(PYTHON).getMember("_mn_compile_runtime_ast");
    }

    private static Context.Builder newContextBuilder(ClassLoader classLoader) {
        return GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(INJECT_RESOURCES)
                .resourceLoadingClass(PythonAstParser.class)
                .build())
            // Future hardening should constrain host access to the required Micronaut API surface.
            .allowHostAccess(HostAccess.ALL)
            .hostClassLoader(classLoader)
            .allowHostClassLookup(name -> name.startsWith("io.micronaut"));
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
    private static Context buildTolerantly(Context.Builder contextBuilder, boolean incremental, ClassLoader classLoader) {
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
        bindings.putMember("src", sources);
        bindings.putMember("package_name", packageName != null ? packageName : "");
        bindings.putMember("visitor_context", visitorContext);
        bindings.putMember("file_name", "Unknown");
        bindings.putMember("src_root", "");
        context.eval(Source.create(
            PYTHON,
            getSource()
        ));
        return new PythonEnvironment(
            classes,
            scripts,
            decorators,
            context
        );
    }

    private static @NotNull String resolveQualifiedName(String packageName, ClassDef classDef) {
        String qualifiedName = classDef.name();
        if (!StringUtils.isEmpty(packageName)) {
            qualifiedName = packageName + "." + qualifiedName;
        }
        return qualifiedName;
    }

    private static @NotNull String resolveScriptQualifiedName(String packageName, ScriptDef scriptDef) {
        String qualifiedName = scriptDef.name();
        if (!StringUtils.isEmpty(packageName)) {
            qualifiedName = packageName + "." + qualifiedName;
        }
        return qualifiedName;
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
        Map<String, DecoratorDef> decorators = new LinkedHashMap<>();
        Map<String, ClassDef> classes = new LinkedHashMap<>();
        Map<String, ScriptDef> scripts = new LinkedHashMap<>();

        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("callback", (Function<Object, Object>) o -> {
            if (o instanceof ClassDef classDef) {
                String qualifiedName = resolveQualifiedName(classDef.packageName(), classDef);
                classes.put(qualifiedName, classDef);
            } else if (o instanceof ScriptDef scriptDef) {
                String qualifiedName = resolveScriptQualifiedName(scriptDef.packageName(), scriptDef);
                scripts.put(qualifiedName, scriptDef);
            } else if (o instanceof DecoratorDef decoratorDef) {
                decorators.put(decoratorDef.annotationName(), decoratorDef);
            }
            return o;
        });

        for (Source source : sources) {
            for (String srcDir : srcDirs) {
                String path = source.getPath();
                if (path == null) {
                    String packageName = getPackageNameOfSource(srcDir, source);
                    bindings.putMember("src", source.getCharacters());
                    bindings.putMember("package_name", packageName);
                    String fileName = source.getName();
                    bindings.putMember("file_name", fileName == null || fileName.isBlank() ? "Unnamed" : fileName);
                    bindings.putMember("visitor_context", visitorContext);
                    bindings.putMember("src_root", srcDir);
                    context.eval(Source.create(
                        PYTHON,
                        getSource()
                    ));
                } else if (isWithinSourceDir(srcDir, path)) {
                    String packageName = getPackageNameOfSource(srcDir, source);
                    bindings.putMember("src", source.getCharacters());
                    bindings.putMember("package_name", packageName);
                    bindings.putMember("file_name", source.getName());
                    bindings.putMember("visitor_context", visitorContext);
                    bindings.putMember("src_root", srcDir);
                    context.eval(Source.create(
                        PYTHON,
                        getSource()
                    ));
                }
            }
        }
        return new PythonEnvironment(
            classes,
            scripts,
            decorators,
            context
        );
    }

    private static boolean isWithinSourceDir(String srcDir, String path) {
        return path.startsWith(srcDir) || path.startsWith("/private" + srcDir);
    }

    public static String getPackageNameOfSource(String srcDir, Source source) {
        String path = source.getPath();
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
        context.eval(Source.create(PYTHON, getCallExtractionSource()));
        return calls;
    }

    public @NotNull List<TransformResult> transform(VisitorContext visitorContext, Source... pythonSource) {
        runtimeArtifacts.clear();
        Value bindings = context.getBindings(PYTHON);
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
                name -> visitorContext.getClassElements(name, "*")
            );
        });
        List<TransformResult> results = new ArrayList<>();
        for (Source source : pythonSource) {
            bindings.putMember("src", source.getCharacters());

            Value result;
            try {
                result = context.eval(Source.create(
                    PYTHON,
                    getTransformSource()
                ));
            } catch (Exception e) {
                StringWriter stack = new StringWriter();
                e.printStackTrace(new PrintWriter(stack));
                throw new ProcessingException(null, "Error processing Python source [" + source.getName() + "]: " + e.getMessage() + System.lineSeparator() + stack, e);
            }
            Map map = result.as(Map.class);
            String code = map.containsKey("code") ? map.get("code").toString() : null;
            String runtimeCode = map.containsKey("runtimeCode") ? map.get("runtimeCode").toString() : code;
            Map<String, String> decorators = map.containsKey("decorators") ? (Map<String, String>) map.get("decorators") : null;
            Map<String, java.util.List<Map<String, String>>> javaClassImports =
                map.containsKey("javaClassImports") ? (Map<String, java.util.List<Map<String, String>>>) map.get("javaClassImports") : null;
            java.util.List<String> exportedTypes = map.containsKey("exportedTypes") ? (java.util.List<String>) map.get("exportedTypes") : new ArrayList<>();
            java.util.List<String> allClassNames = map.containsKey("allClassNames") ? (java.util.List<String>) map.get("allClassNames") : new ArrayList<>();
            java.util.List<String> validationErrors = map.containsKey("validationErrors") ? (java.util.List<String>) map.get("validationErrors") : new ArrayList<>();
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
            runtimeArtifacts.put(
                transformResult,
                new RuntimeArtifact(
                    result.getHashValue("runtimeTree"),
                    result.getHashValue("runtimeRequired").asBoolean()
                )
            );
        }
        return results;
    }

    PythonBytecodeCompiler.Result compileRuntimeBytecode(TransformResult transformResult,
                                                         String filename) {
        RuntimeArtifact artifact = runtimeArtifacts.get(transformResult);
        if (artifact == null) {
            throw new IllegalArgumentException("Unknown Python transform result");
        }
        Value result = runtimeAstCompiler.execute(
            artifact.tree(),
            transformResult.originalSource().getCharacters().toString(),
            filename
        );
        return new PythonBytecodeCompiler.Result(
            result.getArrayElement(0).asString(),
            result.getArrayElement(1).as(byte[].class)
        );
    }

    boolean requiresRuntimeBytecode(TransformResult transformResult) {
        RuntimeArtifact artifact = runtimeArtifacts.get(transformResult);
        return artifact != null && artifact.required();
    }

    private static String normalizeKeywordSafePackageName(String name) {
        String[] parts = name.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.endsWith("_")) {
                String withoutTrailingUnderscore = part.substring(0, part.length() - 1);
                if (PYTHON_KEYWORDS.contains(withoutTrailingUnderscore)) {
                    parts[i] = withoutTrailingUnderscore;
                }
            }
        }
        return String.join(".", parts);
    }

    public PythonEnvironment process(@Language("python") String sources, VisitorContext visitorContext) {
        return process(sources, "", visitorContext);
    }

    public PythonEnvironment process(@Language("python") String sources, String packageName, VisitorContext visitorContext) {
        // First transform the code
        TransformResult transformedCode = transform(visitorContext, sources);

        // Then parse the transformed code
        return parse(transformedCode.code(), packageName);
    }

    private static @Language("python") String getSource() {
        return """
            import ast
            import java
            from micronaut_processor import MicronautAstVisitor

            tree = ast.parse(src)
            MicronautAstVisitor(callback, package_name, file_name, visitor_context, src_root).visit(tree)
            """;
    }

    private static @Language("python") String getTransformSource() {
        return """
            import ast
            from micronaut_transformer import MicronautRuntimeTransformer, MicronautTransformer, unparse

            tree = ast.parse(src)
            transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements)
            transformed_tree = transformer.visit(tree)
            diagnostic_runtime_tree = ast.parse(src)
            diagnostic_runtime_transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements, True)
            transformed_diagnostic_runtime_tree = diagnostic_runtime_transformer.visit(diagnostic_runtime_tree)
            executable_runtime_tree = ast.parse(src)
            original_runtime_tree = ast.dump(executable_runtime_tree, include_attributes=False)
            missing_decorator_code = transformer.get_missing_runtime_decorator_code(executable_runtime_tree)
            runtime_transformer = MicronautRuntimeTransformer(
                callback_get_class_element,
                callback_get_class_elements,
                missing_decorator_code
            )
            transformed_runtime_tree = runtime_transformer.visit(executable_runtime_tree)
            ast.fix_missing_locations(transformed_runtime_tree)
            {
                "code": unparse(transformed_tree),
                "runtimeCode": unparse(transformed_diagnostic_runtime_tree),
                "runtimeTree": transformed_runtime_tree,
                "runtimeRequired": ast.dump(transformed_runtime_tree, include_attributes=False) != original_runtime_tree,
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
        runtimeArtifacts.clear();
        this.context.close();
    }

    private record RuntimeArtifact(Value tree, boolean required) {
    }

    /**
     * The result of a transformation.
     *
     * @param originalSource   The original source
     * @param code             The transformed code
     * @param runtimeCode      The runtime code
     * @param decorators       The decorators
     * @param javaClassImports The Java class imports
     * @param exportedTypes    The types that have Micronaut decorators
     * @param allClassNames    All class names defined in the source
     * @param validationErrors Validation errors found while transforming the source
     */
    @Experimental
    public record TransformResult(
        Source originalSource,
        String code,
        String runtimeCode,
        Map<String, String> decorators,
        Map<String, java.util.List<Map<String, String>>> javaClassImports,
        java.util.List<String> exportedTypes,
        java.util.List<String> allClassNames,
        java.util.List<String> validationErrors) {

        public Source transformedSource() {
            return sourceWithContent(code);
        }

        public Source runtimeSource() {
            return sourceWithContent(runtimeCode);
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
