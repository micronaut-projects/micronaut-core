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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.graalvm.polyglot.Value;

import javax.lang.model.element.Modifier;

/**
 * Generates a simple TargetTypeMapping bean for each Python stub that delegates
 * to the statically generated fromPolyglotValue(Value) method.
 */
public final class TargetTypeMappingGenerator implements TypeElementVisitor<Object, Object> {

    private static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        // Only generate for Java stubs created by PythonStubGenerator, which implement ValueCoercible
        if (!element.isAssignable("io.micronaut.context.python.ValueCoercible")) {
            return;
        }
        String mappingName = element.getName() + "TargetTypeMapping";
        ClassTypeDef thisType = ClassTypeDef.of(element.getName());
        ClassDef.ClassDefBuilder builder = ClassDef.builder(mappingName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(ClassTypeDef.of("jakarta.inject.Singleton"))
            .addSuperinterface(TypeDef.parameterized(
                ClassTypeDef.of("io.micronaut.context.python.TargetTypeMapping"), thisType
            ));

        builder.addMethod(MethodDef.builder("targetType")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.parameterized(Class.class, thisType))
            .build(((aThis, params) -> thisType.getStaticField("class", TypeDef.CLASS).returning())));

        builder.addMethod(MethodDef.builder("convert")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterDef.of("value", TypeDef.of(Value.class)))
            .returns(thisType)
            .build(((aThis, params) -> thisType.invokeStatic(FROM_POLYGLOT_VALUE, thisType, params.getFirst()).returning())));

        SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).ifPresent(sg -> sg.write(builder.build(), context, element));
    }
}
