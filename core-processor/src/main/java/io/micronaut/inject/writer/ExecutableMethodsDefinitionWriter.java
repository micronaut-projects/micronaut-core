/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractExecutableMethodsDefinition;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.bean.definition.builder.Builder;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.OutputObjectDef;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Writes out a {@link io.micronaut.inject.ExecutableMethodsDefinition} class.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@NullUnmarked
@Internal
public class ExecutableMethodsDefinitionWriter implements Builder<OutputObjectDef> {
    public static final String CLASS_SUFFIX = "$Exec";

    public static final Method GET_EXECUTABLE_AT_INDEX_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "getExecutableMethodByIndex", int.class);

    public static final Method REQUIRES_METHOD_PROCESSING_METHOD = ReflectionUtils.getRequiredInternalMethod(ExecutableMethodsDefinition.class, "requiresMethodProcessing");

    private static final Constructor<?> METHOD_REFERENCE_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractExecutableMethodsDefinition.MethodReference.class,
        Class.class,
        AnnotationMetadata.class,
        String.class,
        Argument.class,
        Argument[].class,
        boolean.class,
        boolean.class);

    private static final Constructor<?> SUPER_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(
        AbstractExecutableMethodsDefinition.class,
        AbstractExecutableMethodsDefinition.MethodReference[].class);

    private static final Method GET_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "getMethod", String.class, Class[].class);

    private static final Method AT_INDEX_MATCHED_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "methodAtIndexMatches", int.class, String.class, Class[].class);

    private static final String FIELD_INTERCEPTABLE = "$interceptable";

    private static final int MIN_METHODS_TO_GENERATE_GET_METHOD = 5;

    private final String className;
    private final ClassTypeDef thisType;
    private final String beanDefinitionReferenceClassName;

    private final DispatchWriter methodDispatchWriter;

    private final Set<String> methodNames = new HashSet<>();
    private final AnnotationMetadata annotationMetadataWithDefaults;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;

    private final OriginatingElements originatingElements;
    private boolean requiresMethodProcessing;
    @Nullable
    private ClassTypeDef proxyType;

    public ExecutableMethodsDefinitionWriter(EvaluatedExpressionProcessor evaluatedExpressionProcessor,
                                             AnnotationMetadata annotationMetadataWithDefaults,
                                             String beanDefinitionClassName,
                                             String beanDefinitionReferenceClassName,
                                             OriginatingElements originatingElements) {
        this.originatingElements = originatingElements;
        this.annotationMetadataWithDefaults = annotationMetadataWithDefaults;
        this.evaluatedExpressionProcessor = evaluatedExpressionProcessor;
        this.className = beanDefinitionClassName + CLASS_SUFFIX;
        this.thisType = ClassTypeDef.of(className);
        this.beanDefinitionReferenceClassName = beanDefinitionReferenceClassName;
        this.methodDispatchWriter = new DispatchWriter(className);
    }

    /**
     * By default, when the {@link io.micronaut.context.BeanContext} is started, the
     * {@link io.micronaut.context.processor.ExecutableMethodProcessor} instances unless this method returns true.
     *
     * @return Whether the bean definition requires method processing
     * @see io.micronaut.context.annotation.Executable#processOnStartup()
     */
    public boolean requiresMethodProcessing() {
        return requiresMethodProcessing;
    }

    /**
     * @param proxyType The proxy type
     */
    public void setProxyType(@Nullable ClassTypeDef proxyType) {
        this.proxyType = proxyType;
    }

    /**
     * @return The generated class name.
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return The generated class type.
     */
    public ClassTypeDef getClassTypeDef() {
        return ClassTypeDef.of(className);
    }

    /**
     * Does method support intercepted proxy.
     *
     * @return Does method support intercepted proxy
     */
    public boolean isSupportsInterceptedProxy() {
        return methodDispatchWriter.isHasInterceptedMethod();
    }

    /**
     * Adds the given method to the executable methods definition.
     *
     * @param declaringType The declaring type
     * @param methodElement The method element
     */
    public void addExecutableMethod(TypedElement declaringType, MethodElement methodElement) {
        boolean preprocess = methodElement.isTrue(Executable.class, Executable.MEMBER_PROCESS_ON_STARTUP);
        if (preprocess) {
            requiresMethodProcessing = true;
        }

        evaluatedExpressionProcessor.processEvaluatedExpressions(methodElement);

        methodDispatchWriter.addOrGetMethod(declaringType, methodElement);

        MutableAnnotationMetadata.contributeDefaults(
            annotationMetadataWithDefaults,
            methodElement
        );
    }

    public final void addBridgeMethod(MethodElement methodElement, MethodElement proxyMethod) {
        methodDispatchWriter.addOrGetInterceptedMethod(methodElement.getDeclaringType(), methodElement, proxyMethod);
    }

    public final int findIndexOfExecutableMethod(MethodElement methodElement) {
        int index = methodDispatchWriter.findMethodIndex(methodElement);
        if (index == -1) {
            throw new IllegalStateException("Cannot find the method: " + methodElement);
        }
        return index;
    }

    /**
     * Invoke to build the class model.
     */
    @Override
    public final OutputObjectDef build() {
        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();

        ClassTypeDef thisType = ClassTypeDef.of(className);

        Function<String, ExpressionDef> loadClassValueExpressionFn = AnnotationMetadataGenUtils.createLoadClassValueExpressionFn(thisType, loadTypeMethods);

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(className)
            .synthetic()
            .addAnnotation(Generated.class)
            .superclass(ClassTypeDef.of(AbstractExecutableMethodsDefinition.class));

        ClassTypeDef methodReferenceType = ClassTypeDef.of(AbstractExecutableMethodsDefinition.MethodReference.class);
        TypeDef.Array methodsFieldType = methodReferenceType.array();

        List<MethodDef> metadataMethods = new ArrayList<>();
        for (DispatchWriter.DispatchTarget dispatchTarget : methodDispatchWriter.getDispatchTargets()) {
            TypedElement declaringType = dispatchTarget.getDeclaringType();
            MethodElement methodElement = dispatchTarget.getMethodElement();
            Objects.requireNonNull(methodElement);

            int index = 1;
            String prefix = "$metadata$";
            String methodName = prefix + methodElement.getName();
            while (methodNames.contains(methodName)) {
                methodName = prefix + methodElement.getName() + "$" + (index++);
            }
            methodNames.add(methodName);
            metadataMethods.add(MethodDef.builder(methodName)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .returns(methodReferenceType)
                .build((aThis, methodParameters) -> newNewMethodReference((ClassElement) declaringType, methodElement, loadClassValueExpressionFn).returning()));
        }

        metadataMethods.forEach(classDefBuilder::addMethod);

        // We don't store methods into a static array, the $Exec class is always stored into a static field
        // Otherwise we have a circular initialization problem $Exec -> $Definition.cinit -> new $Exec

        ExpressionDef createMethodsArrayExp = methodsFieldType.instantiate(
            metadataMethods.stream().map(thisType::invokeStatic).toList()
        );

        if (methodDispatchWriter.isHasInterceptedMethod()) {
            ClassTypeDef proxy = Objects.requireNonNull(proxyType, "Proxy type is required");

            FieldDef interceptable = FieldDef.builder(FIELD_INTERCEPTABLE, TypeDef.Primitive.BOOLEAN)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();

            classDefBuilder.addField(interceptable);

            MethodDef constructorWithInterceptable = MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .addParameters(boolean.class)
                .addStatement((aThis, methodParameters) -> aThis.superRef()
                    .invokeConstructor(SUPER_CONSTRUCTOR, createMethodsArrayExp))
                .addStatement((aThis, methodParameters) -> aThis.field(interceptable).put(methodParameters.get(0)))
                .build();

            classDefBuilder.addMethod(
                constructorWithInterceptable
            );
            classDefBuilder.addMethod(
                MethodDef.constructor()
                    .addModifiers(Modifier.PUBLIC)
                    .build((aThis, methodParameters) -> StatementDef.multi(
                            aThis.invokeConstructor(constructorWithInterceptable, ExpressionDef.falseValue())
                        )
                    )
            );

            classDefBuilder.addMethod(methodDispatchWriter.buildDispatchMethod(proxy, interceptable));
        } else {
            classDefBuilder.addMethod(
                MethodDef.constructor()
                    .addModifiers(Modifier.PUBLIC)
                    .build((aThis, methodParameters) -> aThis.superRef()
                        .invokeConstructor(SUPER_CONSTRUCTOR, createMethodsArrayExp)
                    )
            );
            MethodDef dispatchMethod = methodDispatchWriter.buildDispatchMethod();
            if (dispatchMethod != null) {
                classDefBuilder.addMethod(dispatchMethod);
            }
        }

        MethodDef getTargetMethodByIndex = methodDispatchWriter.buildGetTargetMethodByIndex();
        if (getTargetMethodByIndex != null) {
            classDefBuilder.addMethod(getTargetMethodByIndex);
        }
        classDefBuilder.addMethod(
            MethodDef.override(REQUIRES_METHOD_PROCESSING_METHOD).build((aThis, methodParameters) ->
                ExpressionDef.constant(requiresMethodProcessing).returning())
        );

        if (methodDispatchWriter.getDispatchTargets().size() > MIN_METHODS_TO_GENERATE_GET_METHOD) {
            classDefBuilder.addMethod(buildGetMethod());
        }
        loadTypeMethods.values().forEach(classDefBuilder::addMethod);

        return new OutputObjectDef(classDefBuilder.build(), null, originatingElements);
    }

    private MethodDef buildGetMethod() {
        return MethodDef.override(GET_METHOD)
            .build((aThis, methodParameters) -> {
                Map<ExpressionDef.Constant, StatementDef> switchCases = new HashMap<>();
                Map<String, List<DispatchWriter.DispatchTarget>> hashToMethods = new TreeMap<>();
                for (DispatchWriter.DispatchTarget dispatchTarget : methodDispatchWriter.getDispatchTargets()) {
                    MethodElement methodElement = dispatchTarget.getMethodElement();
                    if (methodElement == null) {
                        continue;
                    }
                    hashToMethods.computeIfAbsent(methodElement.getName(), name -> new ArrayList<>()).add(dispatchTarget);
                }

                for (Map.Entry<String, List<DispatchWriter.DispatchTarget>> e : hashToMethods.entrySet()) {
                    List<StatementDef> statements = new ArrayList<>();
                    for (DispatchWriter.DispatchTarget dispatchTarget : e.getValue()) {
                        int index = methodDispatchWriter.getDispatchTargets().indexOf(dispatchTarget);
                        StatementDef statementDef = aThis.superRef().invoke(
                            AT_INDEX_MATCHED_METHOD,

                            ExpressionDef.constant(index),
                            methodParameters.get(0),
                            methodParameters.get(1)
                        ).ifTrue(
                            aThis.superRef().invoke(GET_EXECUTABLE_AT_INDEX_METHOD, ExpressionDef.constant(index)).returning()
                        );
                        statements.add(statementDef);
                    }

                    switchCases.put(ExpressionDef.constant(e.getKey()), StatementDef.multi(statements));
                }

                return StatementDef.multi(
                    methodParameters.get(0)
                        .asStatementSwitch(TypeDef.of(ExecutableMethod.class), switchCases),
                    ExpressionDef.nullValue().returning()
                );
            });
    }

    private ExpressionDef newNewMethodReference(ClassElement declaringType,
                                                MethodElement methodElement,
                                                Function<String, ExpressionDef> loadClassValueExpressionFn) {

        ClassTypeDef methodReferenceType = ClassTypeDef.of(AbstractExecutableMethodsDefinition.MethodReference.class);

        AnnotationMetadata annotationMetadata = methodElement.getTargetAnnotationMetadata();

        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            if (hierarchy.size() != 2) {
                throw new IllegalStateException("Expected the size of 2");
            }
            if (hierarchy.getRootMetadata().equals(methodElement.getOwningType())) {
                annotationMetadata = new AnnotationMetadataHierarchy(
                    new AnnotationMetadataReference(beanDefinitionReferenceClassName, methodElement.getOwningType()),
                    annotationMetadata.getDeclaredMetadata()
                );
            }
        }

        return methodReferenceType.instantiate(
            METHOD_REFERENCE_CONSTRUCTOR,

            // 1: declaringType
            ExpressionDef.constant(ClassTypeDef.of(declaringType)),
            // 2: annotationMetadata
            annotationMetadata(annotationMetadata, loadClassValueExpressionFn),
            // 3: methodName
            ExpressionDef.constant(methodElement.getName()),
            // 4: return argument
            ArgumentExpUtils.pushReturnTypeArgument(annotationMetadataWithDefaults, thisType, declaringType, methodElement.getGenericReturnType(), loadClassValueExpressionFn),
            // 5: arguments
            (methodElement.getSuspendParameters().length == 0 ? ExpressionDef.nullValue() : ArgumentExpUtils.pushBuildArgumentsForMethod(
                annotationMetadataWithDefaults,
                declaringType.getType(),
                thisType,
                Arrays.asList(methodElement.getSuspendParameters()),
                loadClassValueExpressionFn
            )),
            // 6: isAbstract
            ExpressionDef.constant(methodElement.isAbstract()),
            // 7: isSuspend
            ExpressionDef.constant(methodElement.isSuspend())
        );
    }

    private ExpressionDef annotationMetadata(AnnotationMetadata annotationMetadata,
                                             Function<String, ExpressionDef> loadClassValueExpressionFn) {

        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            return ExpressionDef.nullValue();
        }
        return switch (annotationMetadata) {
            case AnnotationMetadataReference annotationMetadataReference ->
                AnnotationMetadataGenUtils.annotationMetadataReference(annotationMetadataReference);
            case AnnotationMetadataHierarchy annotationMetadataHierarchy ->
                AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(annotationMetadataHierarchy, loadClassValueExpressionFn);
            case MutableAnnotationMetadata mutableAnnotationMetadata ->
                AnnotationMetadataGenUtils.instantiateNewMetadata(mutableAnnotationMetadata, loadClassValueExpressionFn);
            default -> throw new IllegalStateException("Unknown metadata: " + annotationMetadata);
        };
    }

    /**
     * Retrieves the total count of methods.
     *
     * @return The number of methods available.
     * @since 5.0
     */
    public int getMethodsCount() {
        return methodDispatchWriter.getDispatchTargets().size();
    }

    /**
     * Retrieves the `MethodElement` at the specified index.
     *
     * @param index The index of the method to retrieve.
     * @return The `MethodElement` corresponding to the specified index.
     * @since 5.0
     */
    public MethodElement getMethodByIndex(int index) {
        return methodDispatchWriter.getDispatchTargets().get(index).getMethodElement();
    }
}
