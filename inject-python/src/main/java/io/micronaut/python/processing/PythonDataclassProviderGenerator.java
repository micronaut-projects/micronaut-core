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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates a bean and service containing the dataclass metadata of a compilation. Class literals
 * and Python class references are computed here and kept in a static list, so context setup only
 * binds the lazy metadata accessors to its own Python context.
 *
 * @since 5.3.0
 */
@Internal
final class PythonDataclassProviderGenerator {

    private static final String PROVIDER = "io.micronaut.context.python.PythonDataclassProvider";
    private static final String CLASS_NAME_PREFIX = "PythonDataclasses$";

    private final VisitorContext context;
    private final Map<String, ClassElement> dataclasses = new TreeMap<>();

    PythonDataclassProviderGenerator(VisitorContext context) {
        this.context = context;
    }

    void referencePythonType(ClassElement element) {
        if (!element.isInterface() && PythonStubGenerator.isPythonDataclassType(element)) {
            dataclasses.putIfAbsent(element.getName(), element);
        }
    }

    void generate(ClassElement originatingElement) {
        if (dataclasses.isEmpty()) {
            return;
        }
        ClassTypeDef entryType = ClassTypeDef.of(PROVIDER + ".Entry");
        TypeDef listType = TypeDef.parameterized(ClassTypeDef.of(List.class), entryType);
        List<ExpressionDef> values = dataclasses.values().stream()
            .map(element -> entryType.instantiate(
                ClassTypeDef.of(element.getCanonicalName(), element.isInner()).getStaticField("class", TypeDef.CLASS),
                PythonStubGenerator.pythonClassReferenceExpression(element)
            ))
            .map(ExpressionDef.class::cast)
            .toList();
        String className = originatingElement.getPackageName() + '.' + CLASS_NAME_PREFIX
            + PythonAnnotationProcessor.contentHash(String.join("\n", dataclasses.keySet()));
        FieldDef entries = FieldDef.builder("ENTRIES")
            .ofType(listType)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(ClassTypeDef.of(List.class).invokeStatic("of", listType, values))
            .build();
        ClassDef provider = ClassDef.builder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Internal.class)
            .addAnnotation(ClassTypeDef.of("jakarta.inject.Singleton"))
            .addSuperinterface(ClassTypeDef.of(PROVIDER))
            .addField(entries)
            .addMethod(MethodDef.builder("entries")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(listType)
                .build((aThis, params) -> ClassTypeDef.of(className).getStaticField(entries).returning()))
            .build();
        SourceGenerators.findByLanguage(VisitorContext.Language.JAVA)
            .ifPresent(generator -> generator.write(provider, context, originatingElement));
        context.visitServiceDescriptor(PROVIDER, className, originatingElement);
    }
}
