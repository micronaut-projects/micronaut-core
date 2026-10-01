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
package io.micronaut.python.annotation.processing.test.bridge;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates, for every class annotated with {@link GenerateCaller}, a Java class named {@code <Class>Caller} with a
 * static method per public instance method of the class that invokes the method on the generated Java class, the
 * way a compile-time visitor (an expression language compiler, a mapper generator) calls the methods it sees on
 * the element.
 */
public class PublicMethodCallerVisitor implements TypeElementVisitor<GenerateCaller, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).orElse(null);
        if (sourceGenerator == null) {
            return;
        }
        ClassTypeDef targetType = ClassTypeDef.of(element.getName());
        ClassDef.ClassDefBuilder builder = ClassDef.builder(element.getName() + "Caller")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        List<MethodElement> methods = element.getEnclosedElements(
            ElementQuery.ALL_METHODS.onlyAccessible().onlyInstance().onlyDeclared().filter(method -> !method.isAbstract())
        );
        for (MethodElement method : methods) {
            MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(method.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(TypeDef.of(method.getReturnType()))
                .addParameter(ParameterDef.of("target", targetType));
            for (ParameterElement parameter : method.getParameters()) {
                methodBuilder.addParameter(ParameterDef.of(parameter.getName(), TypeDef.of(parameter.getType())));
            }
            builder.addMethod(methodBuilder.build((self, parameters) -> {
                List<ExpressionDef> arguments = new ArrayList<>(parameters.subList(1, parameters.size()));
                ExpressionDef call = parameters.get(0).invoke(method.getName(), TypeDef.of(method.getReturnType()), arguments);
                return method.getReturnType().isVoid() ? (StatementDef) call : call.returning();
            }));
        }
        sourceGenerator.write(builder.build(), context, element);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
