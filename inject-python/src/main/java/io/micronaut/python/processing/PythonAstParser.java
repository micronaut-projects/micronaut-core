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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.micronaut.core.util.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.intellij.lang.annotations.Language;

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

    public PythonEnvironment parse(Path... files) throws IOException {
        StringBuilder combinedSources = new StringBuilder();
        for (Path file : files) {
            if (Files.isRegularFile(file) && file.toString().endsWith(".py")) {
                combinedSources.append(Files.readString(file)).append("\n");
            }
        }
        return parse(combinedSources.toString());
    }

    public String transform(@Language("python") String sources, VisitorContext visitorContext) {
        Value bindings = context.getBindings(PYTHON);
        bindings.putMember("src", sources);
        bindings.putMember("callback_get_class_element", (Function<String, Object>) name -> {
            var classElement = visitorContext.getClassElement(name);
            return classElement.orElse(null);
        });
        bindings.putMember("callback_get_class_elements", (Function<String, Object[]>) packageName ->
            visitorContext.getClassElements(packageName, StringUtils.EMPTY_STRING_ARRAY)
        );
        Value result = context.eval(Source.create(
            PYTHON,
            getTransformSource()
        ));
        return result.asString();
    }

    public PythonEnvironment process(@Language("python") String sources, VisitorContext visitorContext) {
        return process(sources, "", visitorContext);
    }

    public PythonEnvironment process(@Language("python") String sources, String packageName, VisitorContext visitorContext) {
        // First transform the code
        String transformedCode = transform(sources, visitorContext);

        // Then parse the transformed code
        return parse(transformedCode, packageName);
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
            unparse(transformed_tree)
            """;
    }

    public void close() {
        this.context.close();
    }
}
