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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.python.processing.element.PythonScriptElement;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.micronaut.python.processing.PythonStubGenerator.AS_POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.PYTHON_CONTEXT_RUNTIME;
import static io.micronaut.python.processing.PythonStubGenerator.FROM_POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.PYTHON_ASYNCIO_RUNTIME;
import static io.micronaut.python.processing.PythonStubGenerator.addReferencedPythonClassReferenceFields;
import static io.micronaut.python.processing.PythonStubGenerator.convertedElementPublisher;
import static io.micronaut.python.processing.PythonStubGenerator.erasedType;
import static io.micronaut.python.processing.PythonStubGenerator.handleReturnType;
import static io.micronaut.python.processing.PythonStubGenerator.PUBLISHER;
import static io.micronaut.python.processing.PythonStubGenerator.isAsyncGeneratorPythonMethod;
import static io.micronaut.python.processing.PythonStubGenerator.isAsyncPythonMethod;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassReference;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassAnnotation;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassReferenceField;
import static io.micronaut.python.processing.PythonStubGenerator.propertyType;

final class PythonPooledStubGenerator {
    private static final ClassTypeDef POLYGLOT_CONTEXT = ClassTypeDef.of("org.graalvm.polyglot.Context");
    private static final String CONTEXT_POOLED = "io.micronaut.context.python.scope.ContextPooled";
    private static final ClassTypeDef POOLED_INSTANCE = ClassTypeDef.of("io.micronaut.context.python.PythonPooledInstance");
    private static final String POOLED_INSTANCE_FIELD = "graalpyPooledInstance";
    private static final String REPORTED_POOLED_DEPENDENCIES = "micronaut.python.reported-pooled-dependencies";

    /**
     * Warns about a pooled type whose Python dependency is pinned to a single context.
     *
     * <p>A pooled type exists once per context, so it gains from holding only references that
     * can exist in whichever context serves a call. A singleton Python bean cannot: it is one
     * instance living in one context, and a pooled instance elsewhere holding a reference to it
     * puts that context's work back through the owning one. That is the cost pooling is there
     * to avoid, and nothing else reports it -- the application runs, and is only slower under
     * concurrency, by more the more contexts there are.
     *
     * <p>A dependency that is not a Python type is unrestricted: a Java bean has no context
     * affinity and no interpreter lock to contend for, so a pooled type may hold as many as it
     * likes.
     *
     * <p>This is a warning rather than an error on purpose. The shape already exists and works:
     * a route module is pooled and commonly injects singleton services, so rejecting it would
     * break code that compiles today for the sake of a performance characteristic. The warning
     * names the cost and leaves the choice.
     *
     * @param context The visitor context, for reporting
     * @param element The pooled Python type
     * @param dependencies Its injected dependencies
     */
    private static void warnAboutContextBoundDependencies(VisitorContext context,
                                                          ClassElement element,
                                                          List<? extends TypedElement> dependencies) {
        for (TypedElement dependency : dependencies) {
            ClassElement dependencyType = dependency.getGenericType();
            if (!isPythonType(dependencyType) || dependencyType.hasStereotype(CONTEXT_POOLED)) {
                continue;
            }
            if (dependencyType.hasStereotype(AnnotationUtil.SINGLETON)) {
                if (!alreadyReported(context, element, dependency)) {
                    context.warn("The pooled type [" + element.getSimpleName() + "] depends on the singleton Python bean ["
                        + dependencyType.getSimpleName() + "] through [" + dependency.getName() + "]. A pooled type exists "
                        + "once per context and a singleton Python bean exists once in one context, so calls through this "
                        + "dependency run in that one context however many the pool has, and the gain from pooling is lost "
                        + "for them. Make [" + dependencyType.getSimpleName() + "] pooled as well, or give it a scope that "
                        + "allows an instance per context. A dependency on a Java type has no such cost.", element);
                }
            }
        }
    }

    /**
     * Whether this pairing has already been reported during this compilation.
     *
     * <p>Main and test sources are processed in separate rounds that visit the same modules, so
     * without this every warning is emitted twice. Recorded on the visitor context rather than in
     * static state, so the set belongs to the compilation and cannot outlive it in a daemon.
     *
     * @param context The visitor context
     * @param element The pooled type
     * @param dependency The dependency warned about
     * @return Whether the pairing was reported before this call
     */
    @SuppressWarnings("unchecked")
    private static boolean alreadyReported(VisitorContext context, ClassElement element, TypedElement dependency) {
        Set<String> reported = context.get(REPORTED_POOLED_DEPENDENCIES, Set.class)
            .map(set -> (Set<String>) set)
            .orElse(null);
        if (reported == null) {
            reported = ConcurrentHashMap.newKeySet();
            context.put(REPORTED_POOLED_DEPENDENCIES, reported);
        }
        return !reported.add(element.getName() + " -> " + dependency.getName());
    }

    /**
     * Whether a type is implemented in Python, and so belongs to a context.
     *
     * @param type The type
     * @return Whether it is a Python type
     */
    /**
     * A module's injected members.
     *
     * <p>Queried as members rather than through {@code ALL_FIELDS}, which returns none of them:
     * a module-level {@code Annotated[T, Inject]} is the shape {@code PythonScriptElement} looks
     * for when it decides the module is a bean, so this asks the same way.
     *
     * @param scriptElement The module
     * @return Its injected members, those of them that have a type
     */
    private static List<TypedElement> injectedMembers(PythonScriptElement scriptElement) {
        return scriptElement.getEnclosedElements(ElementQuery.of(MemberElement.class))
            .stream()
            .filter(member -> member.hasStereotype(AnnotationUtil.INJECT))
            .filter(TypedElement.class::isInstance)
            .map(TypedElement.class::cast)
            .toList();
    }

    private static boolean isPythonType(ClassElement type) {
        return type instanceof AbstractPythonClassElement || type instanceof PythonScriptElement;
    }

    static ClassDef.ClassDefBuilder generatePooledClass(AbstractPythonClassElement element,
                                                        VisitorContext context,
                                                        Map<String, ClassElement> allClasses) {
        String typeName = element.getName();
        var builder = ClassDef.builder(typeName).addModifiers(Modifier.PUBLIC);
        FieldDef pythonClassReference = pythonClassReferenceField("__PYTHON_CLASS_REFERENCE", element);
        builder.addField(pythonClassReference);
        builder.addAnnotation(Vetoed.class);
        builder.addAnnotation(pythonClassAnnotation(element));
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));

        ClassElement superType = element.getSuperType().orElse(null);
        if (superType instanceof AbstractPythonClassElement) {
            builder.superclass(ClassTypeDef.of(PythonStubGenerator.javaTypeName(superType)));
        }

        List<PropertyElement> beanProperties = element.getBeanProperties();
        if (!beanProperties.isEmpty()) {
            throw new ProcessingException(element, "@Pooled does not support introspected bean properties on Python classes.");
        }
        var pythonConstructor = element.getPrimaryConstructor().orElse(null);
        if (pythonConstructor != null) {
            warnAboutContextBoundDependencies(context, element, List.of(pythonConstructor.getParameters()));
        }

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        ParameterElement[] constructorParameters = pythonConstructor == null
            ? new ParameterElement[0]
            : pythonConstructor.getParameters();
        boolean hasConstructorArguments = constructorParameters.length > 0;

        FieldDef pooledInstanceField = null;
        if (hasConstructorArguments) {
            // The bean owns its per-context instances, because the pool's cache is keyed by class
            // and two pooled beans of one class can hold different dependencies. The arguments are
            // captured once, at injection, and used to construct in whichever context serves a call.
            FieldDef pooledInstance = FieldDef.builder(POOLED_INSTANCE_FIELD, POOLED_INSTANCE)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();
            builder.addField(pooledInstance);
            pooledInstanceField = pooledInstance;

            MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
            for (ParameterElement parameter : constructorParameters) {
                ctor.addParameter(ParameterDef.builder(parameter.getName(), erasedType(parameter.getGenericType())).build());
            }
            builder.addMethod(ctor.build(((aThis, params) -> aThis.field(pooledInstance).assign(
                POOLED_INSTANCE.instantiate(
                    List.of(
                        pythonClassReference(element, pythonClassReference),
                        TypeDef.OBJECT.array().instantiate(params)
                    )
                )
            ))));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_CONTEXT_RUNTIME
                    .invokeStatic("findPooledInstance", POLYGLOT_VALUE, List.of(aThis.field(pooledInstance))).returning())));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(POLYGLOT_CONTEXT)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_CONTEXT_RUNTIME
                    .invokeStatic("findPooledInstance", POLYGLOT_VALUE, List.of(
                        aThis.field(pooledInstance),
                        params.getFirst()
                    )).returning())));

            // A wrapper cannot be rebuilt from a bare value here: the dependencies the instance was
            // constructed with are not recoverable from it. Previously unreachable, because a pooled
            // type could not have constructor arguments at all.
            builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(POLYGLOT_VALUE)
                .returns(thisType)
                .build(((aThis, methodParameters) -> ClassTypeDef.of(UnsupportedOperationException.class)
                    .instantiate(ExpressionDef.constant(
                        "Cannot rebuild the pooled Python bean [" + element.getName() + "] from a polyglot value: "
                            + "it is constructed with injected dependencies, which the value does not carry."
                    )).doThrow())));
        } else {
            MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
            builder.addMethod(ctor.build(((aThis, params) -> StatementDef.multi())));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_CONTEXT_RUNTIME
                    .invokeStatic("findPooledClass", POLYGLOT_VALUE, List.of(pythonClassReference(element, pythonClassReference))).returning())));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(POLYGLOT_CONTEXT)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_CONTEXT_RUNTIME
                    .invokeStatic("findPooledClass", POLYGLOT_VALUE, List.of(
                        pythonClassReference(element, pythonClassReference),
                        params.getFirst()
                    )).returning())));

            builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(POLYGLOT_VALUE)
                .returns(thisType)
                .build(((aThis, methodParameters) -> thisType.instantiate().returning())));
        }

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
        addReferencedPythonClassReferenceFields(builder, element, methodsToBridge);

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledClass(methodElement, builder, element, allClasses, pooledInstanceField);
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

        // A route module is the common pooled type with dependencies, and typically injects
        // services. Those are what the warning is about.
        warnAboutContextBoundDependencies(context, scriptElement, injectedMembers(scriptElement));

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
            .build(((aThis, params) -> PYTHON_CONTEXT_RUNTIME
                .invokeStatic("findPooledScript", POLYGLOT_VALUE,
                    List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script)))
                .returning())));

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(POLYGLOT_CONTEXT)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> PYTHON_CONTEXT_RUNTIME
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
                                                   Map<String, ClassElement> allClasses,
                                                   @Nullable FieldDef pooledInstanceField) {
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
                parameterExpressions.add(methodParameters.get(i));
            }
            // A bean that owns its per-context instances is invoked through the holder; one
            // without arguments still goes through the pool's class cache.
            boolean ownsInstances = pooledInstanceField != null;
            List<ExpressionDef> args = new ArrayList<>();
            args.add(ownsInstances ? aThis.field(pooledInstanceField) : pythonClassReference(element, element));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            if (isAsyncGeneratorPythonMethod(methodElement)) {
                return convertedElementPublisher(allClasses, methodElement.getGenericReturnType(),
                    PYTHON_CONTEXT_RUNTIME.invokeStatic(
                        ownsInstances ? "invokePooledInstancePublisher" : "invokePooledPublisher",
                        ClassTypeDef.of(PUBLISHER), args)).returning();
            }
            if (isAsyncPythonMethod(methodElement) && !methodElement.getReturnType().isVoid()) {
                // the coroutine is driven while the pooled context is still leased to this call
                return completionStage(methodElement, PYTHON_CONTEXT_RUNTIME.invokeStatic(
                    ownsInstances ? "invokePooledInstanceAsync" : "invokePooledAsync",
                    TypeDef.of(CompletionStage.class), args));
            }
            var invoked = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                ownsInstances ? "invokePooledInstance" : "invokePooled", POLYGLOT_VALUE, args);
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
                parameterExpressions.add(methodParameters.get(i));
            }
            List<ExpressionDef> args = new ArrayList<>();
            args.add(ExpressionDef.constant(pkg));
            args.add(ExpressionDef.constant(script));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            if (isAsyncGeneratorPythonMethod(methodElement)) {
                return convertedElementPublisher(allClasses, methodElement.getGenericReturnType(),
                    PYTHON_CONTEXT_RUNTIME.invokeStatic("invokePooledScriptPublisher", ClassTypeDef.of(PUBLISHER), args)).returning();
            }
            if (isAsyncPythonMethod(methodElement) && !methodElement.getReturnType().isVoid()) {
                return completionStage(methodElement, PYTHON_CONTEXT_RUNTIME.invokeStatic("invokePooledScriptAsync", TypeDef.of(CompletionStage.class), args));
            }
            var invoked = PYTHON_CONTEXT_RUNTIME.invokeStatic("invokePooledScript", POLYGLOT_VALUE, args);
            return bridgeReturnValue(allClasses, methodElement, invoked);
        })));

    }

    private static StatementDef completionStage(MethodElement methodElement, ExpressionDef stage) {
        return stage.cast(TypeDef.of(methodElement.getGenericReturnType())).returning();
    }

    private static StatementDef bridgeReturnValue(Map<String, ClassElement> allClasses,
                                                  MethodElement methodElement,
                                                  ExpressionDef.InvokeStaticMethod invoked) {
        if (methodElement.getReturnType().isVoid()) {
            return invoked;
        }
        if (isAsyncGeneratorPythonMethod(methodElement)) {
            return invoked.newLocal("pythonAsyncGenerator", pythonAsyncGenerator ->
                convertedElementPublisher(allClasses, methodElement.getGenericReturnType(), PYTHON_ASYNCIO_RUNTIME.invokeStatic(
                    "generatorToPublisher",
                    ClassTypeDef.of(PUBLISHER),
                    pythonAsyncGenerator
                )).returning()
            );
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
            var invoked = PYTHON_CONTEXT_RUNTIME.invokeStatic("invokePooledScript", POLYGLOT_VALUE,
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
            parameters.add(methodParameters.getFirst());
            var result = PYTHON_CONTEXT_RUNTIME.invokeStatic(adaptAsyncMembers ? "injectPooledScriptAsync" : "injectPooledScript", TypeDef.VOID, parameters);
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
