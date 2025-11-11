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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.intellij.lang.annotations.Language;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;

public final class PythonAstParser {

    public static final String PYTHON = "python";
    public static final String INJECT_RESOURCES = "GRAALPY-VFS/io.micronaut/micronaut-inject-python";
    private final Context context;

    public PythonAstParser() {
        this.context = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(INJECT_RESOURCES)
                .build())
            // TODO: constrain this in future
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> name.startsWith("io.micronaut"))
            .build();
        context.initialize(PYTHON);
    }

    public PythonEnvironment parse(@Language("python") String sources) {
        return parse(sources, "");
    }

    public PythonEnvironment parse(@Language("python") String sources, String packageName) {
        try {
            Map<String, DecoratorDef> decorators = new LinkedHashMap<>();
            Map<String, ClassDef> classes = new LinkedHashMap<>();

            Value bindings = context.getBindings(PYTHON);
            bindings.putMember("callback", (Function<Object, Object>) o -> {
                if (o instanceof ClassDef classDef) {
                    classes.put(classDef.name(), classDef);
                } else if (o instanceof DecoratorDef decoratorDef) {
                    decorators.put(decoratorDef.annotationName(), decoratorDef);
                }
                return o;
            });
            bindings.putMember("src", sources);
            bindings.putMember("package_name", packageName != null ? packageName : "");
            context.eval(Source.create(
                PYTHON,
                getSource()
            ));
            return new PythonEnvironment(
                classes,
                decorators,
                context
            );
        } catch (Exception e) {
            throw e;
        }
    }

    public TransformResult transform(@Language("python") String sources, VisitorContext visitorContext) {
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("src", sources);
        bindings.putMember("callback_get_class_element", (Function<String, Object>) name -> {
            // Transform package names back from "micronaut." to "io.micronaut." for Java lookups
            String javaName = name.startsWith("micronaut.") ? "io." + name : name;
            var classElement = visitorContext.getClassElement(javaName);
            return classElement.orElse(null);
        });
        bindings.putMember("callback_get_class_elements", (Function<String, Object[]>) packageName -> {
            // Transform package names back from "micronaut." to "io.micronaut." for Java lookups
            String javaPackageName = packageName.startsWith("micronaut.") ? "io." + packageName : packageName;
            return visitorContext.getClassElements(javaPackageName, StringUtils.EMPTY_STRING_ARRAY);
        });
        Value result = context.eval(Source.create(
            PYTHON,
            getTransformSource()
        ));
        Map map = result.as(Map.class);
        String code = map.containsKey("code") ? map.get("code").toString() : null;
        Map<String, String> decorators = map.containsKey("decorators") ? (Map<String, String>) map.get("decorators") : null;
        Map<String, java.util.List<Map<String, String>>> javaClassImports =
            map.containsKey("javaClassImports") ? (Map<String, java.util.List<Map<String, String>>>) map.get("javaClassImports") : null;
        return new TransformResult(
            code,
            decorators,
            javaClassImports
        );
    }

    public PythonEnvironment process(@Language("python") String sources, VisitorContext visitorContext) {
        return process(sources, "", visitorContext);
    }

    public PythonEnvironment process(@Language("python") String sources, String packageName, VisitorContext visitorContext) {
        // First transform the code
        TransformResult transformedCode = transform(sources, visitorContext);

        // Then parse the transformed code
        return parse(transformedCode.code(), packageName);
    }

    private static @Language("python") String getSource() {
        return """
            import ast
            import java
            from micronaut_processor import PrintNodeVisitor

            tree = ast.parse(src)
            PrintNodeVisitor(callback, package_name).visit(tree)
            """;
    }

    private static @Language("python") String getTransformSource() {
        return """
            import ast
            from micronaut_transformer import MicronautTransformer, unparse

            tree = ast.parse(src)
            transformer = MicronautTransformer(callback_get_class_element, callback_get_class_elements)
            transformed_tree = transformer.visit(tree)
            {
                "code": unparse(transformed_tree),
                "decorators": transformer.get_generated_decorator_code(),
                "javaClassImports": transformer.java_class_imports
            }
            """;
    }

    public void close() {
        this.context.close();
    }

    /**
     * The result of a transformation
     * @param code The transformed code
     * @param decorators The decorators
     * @param javaClassImports The Java class imports
     */
    public record TransformResult(
        String code, Map<String, String> decorators,
        Map<String, java.util.List<Map<String, String>>> javaClassImports) {}
}
