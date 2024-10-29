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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Writes out a {@link io.micronaut.inject.ExecutableMethodsDefinition} class.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public class ExecutableMethodsDefinitionWriter implements ClassOutputWriter {
    public static final String CLASS_SUFFIX = "$Exec";

    public static final Method GET_EXECUTABLE_AT_INDEX_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "getExecutableMethodByIndex", int.class);

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

    private static final String FIELD_METHODS_REFERENCES = "$METHODS_REFERENCES";
    private static final String FIELD_INTERCEPTABLE = "$interceptable";

    private static final int MIN_METHODS_TO_GENERATE_GET_METHOD = 5;

    private final String className;
    private final ClassTypeDef thisType;
    private final String beanDefinitionReferenceClassName;

    private final List<String> addedMethods = new ArrayList<>();

    private final DispatchWriter methodDispatchWriter;

    private final Set<String> methodNames = new HashSet<>();
    private final AnnotationMetadata annotationMetadataWithDefaults;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;
    private ClassDef.ClassDefBuilder classDefBuilder;

    private final OriginatingElements originatingElements;

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
        this.methodDispatchWriter = new DispatchWriter();
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

    private MethodElement getMethodElement(int index) {
        return methodDispatchWriter.getDispatchTargets().get(index).getMethodElement();
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
     * Is the method abstract.
     *
     * @param index The method index
     * @return Is the method abstract
     */
    public boolean isAbstract(int index) {
        MethodElement methodElement = getMethodElement(index);
        return (isInterface(index) && !methodElement.isDefault()) || methodElement.isAbstract();
    }

    /**
     * Is the method in an interface.
     *
     * @param index The method index
     * @return Is the method in an interface
     */
    public boolean isInterface(int index) {
        return getMethodElement(index).getDeclaringType().isInterface();
    }

    /**
     * Is the method a default method.
     *
     * @param index The method index
     * @return Is the method a default method
     */
    public boolean isDefault(int index) {
        return getMethodElement(index).isDefault();
    }

    /**
     * Is the method suspend.
     *
     * @param index The method index
     * @return Is the method suspend
     */
    public boolean isSuspend(int index) {
        return getMethodElement(index).isSuspend();
    }

    /**
     * Visit a method that is to be made executable allow invocation of said method without reflection.
     *
     * @param declaringType                    The declaring type of the method. Either a Class or a string representing the
     *                                         name of the type
     * @param methodElement                    The method element
     * @param interceptedProxyClassName        The intercepted proxy class name
     * @param interceptedProxyBridgeMethodName The intercepted proxy bridge method name
     * @return The method index
     */
    public int visitExecutableMethod(TypedElement declaringType,
                                     MethodElement methodElement,
                                     String interceptedProxyClassName,
                                     String interceptedProxyBridgeMethodName) {
        evaluatedExpressionProcessor.processEvaluatedExpressions(methodElement);

        String methodKey = methodElement.getName() +
            "(" +
            Arrays.stream(methodElement.getSuspendParameters())
                .map(p -> toTypeString(p.getType()))
                .collect(Collectors.joining(",")) +
            ")";

        int index = addedMethods.indexOf(methodKey);
        if (index > -1) {
            return index;
        }
        addedMethods.add(methodKey);
        if (interceptedProxyClassName == null) {
            return methodDispatchWriter.addMethod(declaringType, methodElement);
        } else {
            return methodDispatchWriter.addInterceptedMethod(declaringType, methodElement, interceptedProxyClassName, interceptedProxyBridgeMethodName);
        }
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className, originatingElements.getOriginatingElements())) {
            outputStream.write(new ByteCodeWriter().write(classDefBuilder.build()));
        }
    }

    /**
     * Invoke to build the class model.
     */
    public final void visitDefinitionEnd() {
        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();

        ClassTypeDef thisType = ClassTypeDef.of(className);

        classDefBuilder = ClassDef.builder(className)
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
                .build((aThis, methodParameters) -> newNewMethodReference((ClassElement) declaringType, methodElement, loadTypeMethods).returning()));
        }

        metadataMethods.forEach(classDefBuilder::addMethod);

        FieldDef methodReferencesField = FieldDef.builder(FIELD_METHODS_REFERENCES, methodsFieldType)
            .addModifiers(Modifier.STATIC, Modifier.FINAL, Modifier.PRIVATE)
            .initializer(
                methodsFieldType.instantiate(
                    metadataMethods.stream().map(thisType::invokeStatic).toList()
                )
            )
            .build();

        classDefBuilder.addField(methodReferencesField);

        if (methodDispatchWriter.isHasInterceptedMethod()) {
            FieldDef interceptable = FieldDef.builder(FIELD_INTERCEPTABLE, TypeDef.Primitive.BOOLEAN)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();

            classDefBuilder.addField(interceptable);

            MethodDef constructorWithInterceptable = MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .addParameters(boolean.class)
                .addStatement((aThis, methodParameters) -> aThis.superRef()
                    .invokeConstructor(SUPER_CONSTRUCTOR, thisType.getStaticField(methodReferencesField)))
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
        } else {
            classDefBuilder.addMethod(
                MethodDef.constructor()
                    .addModifiers(Modifier.PUBLIC)
                    .build((aThis, methodParameters) -> aThis.superRef()
                        .invokeConstructor(SUPER_CONSTRUCTOR, thisType.getStaticField(methodReferencesField))
                    )
            );
        }

        MethodDef dispatchMethod = methodDispatchWriter.buildDispatchMethod();
        if (dispatchMethod != null) {
            classDefBuilder.addMethod(dispatchMethod);
        }
        MethodDef getTargetMethodByIndex = methodDispatchWriter.buildGetTargetMethodByIndex();
        if (getTargetMethodByIndex != null) {
            classDefBuilder.addMethod(getTargetMethodByIndex);
        }

        if (methodDispatchWriter.getDispatchTargets().size() > MIN_METHODS_TO_GENERATE_GET_METHOD) {
            classDefBuilder.addMethod(buildGetMethod());
        }
        loadTypeMethods.values().forEach(classDefBuilder::addMethod);
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
                                                Map<String, MethodDef> loadTypeMethods) {

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
            annotationMetadata(annotationMetadataWithDefaults, annotationMetadata, loadTypeMethods),
            // 3: methodName
            ExpressionDef.constant(methodElement.getName()),
            // 4: return argument
            ArgumentExpUtils.pushReturnTypeArgument(annotationMetadataWithDefaults, thisType, declaringType, methodElement.getGenericReturnType(), loadTypeMethods),
            // 5: arguments
            (methodElement.getSuspendParameters().length == 0 ? ExpressionDef.nullValue() : ArgumentExpUtils.pushBuildArgumentsForMethod(
                annotationMetadataWithDefaults,
                declaringType.getType(),
                thisType,
                Arrays.asList(methodElement.getSuspendParameters()),
                loadTypeMethods
            )),
            // 6: isAbstract
            ExpressionDef.constant(methodElement.isAbstract()),
            // 7: isSuspend
            ExpressionDef.constant(methodElement.isSuspend())
        );
    }

    private ExpressionDef annotationMetadata(AnnotationMetadata annotationMetadataWithDefaults,
                                             AnnotationMetadata annotationMetadata,
                                             Map<String, MethodDef> loadTypeMethods) {

        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            return ExpressionDef.nullValue();
        } else if (annotationMetadata instanceof AnnotationMetadataReference annotationMetadataReference) {
            return AnnotationMetadataGenUtils.annotationMetadataReference(annotationMetadataReference);
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                annotationMetadataHierarchy
            );
            return AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(thisType, annotationMetadataHierarchy, loadTypeMethods);
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            MutableAnnotationMetadata.contributeDefaults(
                annotationMetadataWithDefaults,
                annotationMetadata
            );
            return AnnotationMetadataGenUtils.instantiateNewMetadata(thisType, mutableAnnotationMetadata, loadTypeMethods);
        } else {
            throw new IllegalStateException("Unknown metadata: " + annotationMetadata);
        }
    }

    /**
     * @param p The class element
     * @return The string representation
     */
    private static String toTypeString(ClassElement p) {
        String name = p.getName();
        if (p.isArray()) {
            return name + IntStream.range(0, p.getArrayDimensions()).mapToObj(ignore -> "[]").collect(Collectors.joining());
        }
        return name;
    }
}
