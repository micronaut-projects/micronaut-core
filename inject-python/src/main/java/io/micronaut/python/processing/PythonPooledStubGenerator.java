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

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.PythonScriptElement;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.micronaut.python.processing.PythonStubGenerator.AS_POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.CONTEXT_HOLDER;
import static io.micronaut.python.processing.PythonStubGenerator.FROM_POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.PYTHON_ASYNCIO_RUNTIME;
import static io.micronaut.python.processing.PythonStubGenerator.coerceParameterToPolyglotValue;
import static io.micronaut.python.processing.PythonStubGenerator.erasedType;
import static io.micronaut.python.processing.PythonStubGenerator.handleReturnType;
import static io.micronaut.python.processing.PythonStubGenerator.isAsyncPythonMethod;
import static io.micronaut.python.processing.PythonStubGenerator.propertyType;

final class PythonPooledStubGenerator {
    private static final ClassTypeDef POLYGLOT_CONTEXT = ClassTypeDef.of("org.graalvm.polyglot.Context");

    static ClassDef.ClassDefBuilder generatePooledClass(AbstractPythonClassElement element,
                                                        VisitorContext context,
                                                        Map<String, ClassElement> allClasses) {
        String typeName = element.getName();
        var builder = ClassDef.builder(typeName).addModifiers(Modifier.PUBLIC);
        builder.addAnnotation(Vetoed.class);
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));

        ClassElement superType = element.getSuperType().orElse(null);
        if (superType instanceof AbstractPythonClassElement) {
            builder.superclass(ClassTypeDef.of(superType.getName()));
        }

        List<PropertyElement> beanProperties = element.getBeanProperties();
        if (!beanProperties.isEmpty()) {
            throw new ProcessingException(element, "@Pooled does not support introspected bean properties on Python classes.");
        }
        var pythonConstructor = element.getPrimaryConstructor().orElse(null);
        if (pythonConstructor != null && pythonConstructor.getParameters().length > 0) {
            throw new ProcessingException(element, "@Pooled types must be stateless. Constructor with parameters is not supported.");
        }

        MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
        builder.addMethod(ctor.build(((aThis, params) -> StatementDef.multi())));

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> CONTEXT_HOLDER
                .invokeStatic("findPooledClass", POLYGLOT_VALUE, List.of(
                    ExpressionDef.constant(element.getPackageName()),
                    ExpressionDef.constant(element.getSimpleName())
                )).returning())));

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(POLYGLOT_CONTEXT)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> CONTEXT_HOLDER
                .invokeStatic("findPooledClass", POLYGLOT_VALUE, List.of(
                    ExpressionDef.constant(element.getPackageName()),
                    ExpressionDef.constant(element.getSimpleName()),
                    params.getFirst()
                )).returning())));

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build(((aThis, methodParameters) -> thisType.instantiate().returning())));

        List<MethodElement> methodsToBridge = element.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyInstance()
                .onlyDeclared()
                .annotated(ann -> ann.hasStereotype(Around.class)
                    || ann.hasStereotype(AnnotationUtil.SCOPE)
                    || isDeclaredBeanMethod(ann)
                    || ann.hasStereotype("io.micronaut.context.annotation.Executable"))
        );

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledClass(methodElement, builder, element, allClasses);
        }

        return builder;
    }

    static ClassDef.ClassDefBuilder generatePooledScript(PythonScriptElement scriptElement,
                                                         VisitorContext context,
                                                         Map<String, ClassElement> allClasses) {
        String typeName = scriptElement.getName();
        var builder = ClassDef.builder(scriptElement.getPackageName() + "." + scriptElement.getSimpleName())
            .addModifiers(Modifier.PUBLIC);
        builder.addAnnotation(Vetoed.class);
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));

        MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
        builder.addMethod(ctor.build(((aThis, params) -> StatementDef.multi())));

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        String name = scriptElement.getNativeType().name();
        if (name.endsWith(".py")) {
            name = name.substring(0, name.length() - 3);
        }

        String pkg = scriptElement.getPackageName();
        String script = name;

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> CONTEXT_HOLDER
                .invokeStatic("findPooledScript", POLYGLOT_VALUE,
                    List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script)))
                .returning())));

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(POLYGLOT_CONTEXT)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> CONTEXT_HOLDER
                .invokeStatic("findPooledScript", POLYGLOT_VALUE,
                    List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script), params.getFirst()))
                .returning())));

        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build(((aThis, methodParameters) -> thisType.instantiate().returning())));

        List<MethodElement> methodsToBridge = scriptElement.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyInstance()
                .onlyDeclared()
                .annotated(ann -> ann.hasStereotype(Around.class)
                    || ann.hasStereotype(AnnotationUtil.SCOPE)
                    || isDeclaredBeanMethod(ann)
                    || ann.hasStereotype("io.micronaut.context.annotation.Executable"))
        );
        boolean hasAsyncBridgeMethod = methodsToBridge.stream().anyMatch(PythonStubGenerator::isAsyncPythonMethod);

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledScript(methodElement, builder, pkg, script, allClasses);
        }

        List<PropertyElement> beanProperties = scriptElement.getBeanProperties();
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                addSetterScriptPooled(beanProperty, builder, pkg, script, hasAsyncBridgeMethod);
            }
            if (beanProperty.hasStereotype(Bean.class)) {
                addGetterScriptPooled(beanProperty, builder, pkg, script, allClasses);
            }
        }

        return builder;
    }

    private static void addBridgeMethodPooledClass(MethodElement methodElement,
                                                   ClassDef.ClassDefBuilder builder,
                                                   AbstractPythonClassElement element,
                                                   Map<String, ClassElement> allClasses) {
        String pythonFunctionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.of(methodElement.getGenericReturnType()));

        for (ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getGenericType());
            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), parameterType).build());
        }

        builder.addMethod(methodBuilder.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameterExpressions = new ArrayList<>();
            for (int i = 0; i < methodElement.getParameters().length; i++) {
                ParameterElement parameter = methodElement.getParameters()[i];
                VariableDef.MethodParameter mp = methodParameters.get(i);
                coerceParameterToPolyglotValue(parameter, parameterExpressions, mp);
            }
            List<ExpressionDef> args = new ArrayList<>();
            args.add(ExpressionDef.constant(element.getPackageName()));
            args.add(ExpressionDef.constant(element.getSimpleName()));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            var invoked = CONTEXT_HOLDER.invokeStatic("invokePooled", POLYGLOT_VALUE, args);
            return bridgeReturnValue(allClasses, methodElement, invoked);
        })));

    }

    private static void addBridgeMethodPooledScript(MethodElement methodElement,
                                                    ClassDef.ClassDefBuilder builder,
                                                    String pkg,
                                                    String script,
                                                    Map<String, ClassElement> allClasses) {
        String pythonFunctionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.of(methodElement.getGenericReturnType()));

        for (ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getGenericType());
            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), parameterType).build());
        }

        builder.addMethod(methodBuilder.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameterExpressions = new ArrayList<>();
            for (int i = 0; i < methodElement.getParameters().length; i++) {
                ParameterElement parameter = methodElement.getParameters()[i];
                VariableDef.MethodParameter mp = methodParameters.get(i);
                coerceParameterToPolyglotValue(parameter, parameterExpressions, mp);
            }
            List<ExpressionDef> args = new ArrayList<>();
            args.add(ExpressionDef.constant(pkg));
            args.add(ExpressionDef.constant(script));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            var invoked = CONTEXT_HOLDER.invokeStatic("invokePooledScript", POLYGLOT_VALUE, args);
            return bridgeReturnValue(allClasses, methodElement, invoked);
        })));

    }

    private static StatementDef bridgeReturnValue(Map<String, ClassElement> allClasses,
                                                  MethodElement methodElement,
                                                  ExpressionDef.InvokeStaticMethod invoked) {
        if (methodElement.getReturnType().isVoid()) {
            return invoked;
        }
        if (isAsyncPythonMethod(methodElement)) {
            return invoked.newLocal("pythonCoroutine", pythonCoroutine ->
                PYTHON_ASYNCIO_RUNTIME.invokeStatic(
                    "toCompletionStage",
                    TypeDef.of(CompletionStage.class),
                    pythonCoroutine
                ).cast(TypeDef.of(CompletionStage.class)).cast(TypeDef.of(methodElement.getGenericReturnType())).returning()
            );
        }
        return handleReturnType(allClasses, methodElement.getGenericReturnType(), invoked).returning();
    }

    private static void addGetterScriptPooled(PropertyElement beanProperty,
                                              ClassDef.ClassDefBuilder builder,
                                              String pkg,
                                              String script,
                                              Map<String, ClassElement> allClasses) {
        TypeDef propertyType = propertyType(beanProperty);
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            var invoked = CONTEXT_HOLDER.invokeStatic("invokePooledScript", POLYGLOT_VALUE,
                List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script), ExpressionDef.constant(beanProperty.getName())));
            return handleReturnType(allClasses, beanProperty.getGenericType(), invoked).returning();
        })));
    }

    private static void addSetterScriptPooled(PropertyElement beanProperty,
                                              ClassDef.ClassDefBuilder builder,
                                              String pkg,
                                              String script,
                                              boolean adaptAsyncMembers) {
        TypeDef returnType = beanProperty.getWriteMethod().map(MethodElement::getReturnType).map(TypeDef::of).orElse(TypeDef.VOID);
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(propertyType(beanProperty));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(pkg));
            parameters.add(ExpressionDef.constant(script));
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(beanProperty, parameters, methodParameters.getFirst());
            var result = CONTEXT_HOLDER.invokeStatic(adaptAsyncMembers ? "injectPooledScriptAsync" : "injectPooledScript", TypeDef.VOID, parameters);
            if (returnType.equals(TypeDef.VOID)) {
                return result;
            } else {
                return StatementDef.multi(result, ExpressionDef.nullValue().returning());
            }
        })));
    }

    private static boolean isDeclaredBeanMethod(AnnotationMetadata annotationMetadata) {
        // Visitors can add @Bean directly to Python methods after metadata parsing. Those
        // methods still need Java bridge methods so generated bean definitions can call them.
        return annotationMetadata.hasDeclaredAnnotation(Bean.class)
            || annotationMetadata.hasDeclaredStereotype(Bean.class);
    }
}
