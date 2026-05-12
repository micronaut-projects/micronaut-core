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

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class PythonAstParser {

    public static final String PYTHON = "python";
    public static final String INJECT_RESOURCES = "GRAALPY-VFS/io.micronaut/micronaut-inject-python";
    private static final Set<String> PYTHON_KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    );
    private final Context context;

    public PythonAstParser() {
        this(PythonAstParser.class.getClassLoader());
    }

    PythonAstParser(ClassLoader classLoader) {
        this.context = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(INJECT_RESOURCES)
                .resourceLoadingClass(PythonAstParser.class)
                .build())
            // TODO: constrain this in future
            .allowHostAccess(HostAccess.ALL)
            .hostClassLoader(classLoader)
            .allowHostClassLookup(name -> name.startsWith("io.micronaut"))
            .build();
        context.initialize(PYTHON);
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
                    bindings.putMember("file_name", "Unnamed");
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

    public @NotNull List<TransformResult> transform(VisitorContext visitorContext, Source... pythonSource) {
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("callback_get_class_element", (Function<String, Object>) name -> {
            // Transform package names back from "micronaut." to "io.micronaut." for Java lookups
            name = normalizeKeywordSafePackageName(name);
            String javaName = name.startsWith("micronaut.") ? "io." + name : name;
            var classElement = visitorContext.getClassElement(javaName);
            return classElement.orElse(null);
        });
        bindings.putMember("callback_get_class_elements", (Function<String, Object[]>) packageName -> {
            // Transform package names back from "micronaut." to "io.micronaut." for Java lookups
            packageName = normalizeKeywordSafePackageName(packageName);
            String javaPackageName = packageName.startsWith("micronaut.") ? "io." + packageName : packageName;
            return visitorContext.getClassElements(javaPackageName, "*");
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
            results.add(new TransformResult(
                source,
                code,
                runtimeCode,
                decorators,
                javaClassImports,
                exportedTypes,
                allClassNames
            ));
        }
        return results;
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
            from micronaut_transformer import MicronautTransformer, unparse

            tree = ast.parse(src)
            transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements)
            transformed_tree = transformer.visit(tree)
            runtime_tree = ast.parse(src)
            runtime_transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements, True)
            transformed_runtime_tree = runtime_transformer.visit(runtime_tree)
            {
                "code": unparse(transformed_tree),
                "runtimeCode": unparse(transformed_runtime_tree),
                "decorators": transformer.get_generated_decorator_code(),
                "javaClassImports": transformer.java_class_imports,
                "exportedTypes": transformer.get_exported_types(),
                "allClassNames": transformer.all_class_names
            }
            """;
    }

    public void close() {
        this.context.close();
    }

    /**
     * The result of a transformation
     *
     * @param originalSource   The original source
     * @param code             The transformed code
     * @param runtimeCode      The runtime code
     * @param decorators       The decorators
     * @param javaClassImports The Java class imports
     * @param exportedTypes    The types that have Micronaut decorators
     * @param allClassNames    All class names defined in the source
     */
    public record TransformResult(
        Source originalSource,
        String code,
        String runtimeCode,
        Map<String, String> decorators,
        Map<String, java.util.List<Map<String, String>>> javaClassImports,
        java.util.List<String> exportedTypes,
        java.util.List<String> allClassNames) {

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
