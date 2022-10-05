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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.JavaModelUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.TableSwitchGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Writes out a {@link io.micronaut.inject.ExecutableMethodsDefinition} class.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public class ExecutableMethodsDefinitionWriter extends AbstractClassFileWriter implements Opcodes {
    public static final String CLASS_SUFFIX = "$Exec";

    public static final Method GET_EXECUTABLE_AT_INDEX_METHOD = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "getExecutableMethodByIndex", int.class)
    );

    public static final Type SUPER_TYPE = Type.getType(AbstractExecutableMethodsDefinition.class);

    private static final Method SUPER_CONSTRUCTOR = Method.getMethod(ReflectionUtils.getRequiredInternalConstructor(
                    AbstractExecutableMethodsDefinition.class,
                    AbstractExecutableMethodsDefinition.MethodReference[].class)
            );

    private static final Method WITH_INTERCEPTED_CONSTRUCTOR = new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(boolean.class));

    private static final Method GET_METHOD = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "getMethod", String.class, Class[].class)
    );

    private static final Method AT_INDEX_MATCHED_METHOD = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethodsDefinition.class, "methodAtIndexMatches", int.class, String.class, Class[].class)
    );

    private static final String FIELD_METHODS_REFERENCES = "$METHODS_REFERENCES";
    private static final String FIELD_INTERCEPTABLE = "$interceptable";

    private static final int MIN_METHODS_TO_GENERATE_GET_METHOD = 5;

    private final String className;
    private final String internalName;
    private final Type thisType;
    private final String beanDefinitionReferenceClassName;

    private final Map<String, Integer> defaultsStorage = new HashMap<>();
    private final Map<String, GeneratorAdapter> loadTypeMethods = new LinkedHashMap<>();
    private final List<String> addedMethods = new ArrayList<>();

    private final DispatchWriter methodDispatchWriter;

    public ExecutableMethodsDefinitionWriter(String beanDefinitionClassName,
                                             String beanDefinitionReferenceClassName,
                                             OriginatingElements originatingElements) {
        super(originatingElements);
        this.className = beanDefinitionClassName + CLASS_SUFFIX;
        this.internalName = getInternalName(className);
        this.thisType = Type.getObjectType(internalName);
        this.beanDefinitionReferenceClassName = beanDefinitionReferenceClassName;
        this.methodDispatchWriter = new DispatchWriter(thisType);
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
    public Type getClassType() {
        return thisType;
    }

    private MethodElement getMethodElement(int index) {
        return ((DispatchWriter.MethodDispatchTarget) methodDispatchWriter.getDispatchTargets().get(index)).methodElement;
    }

    /**
     * Does method supports intercepted proxy.
     *
     * @return Does method supports intercepted proxy
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

        String methodKey = methodElement.getName() +
                "(" +
                Arrays.stream(methodElement.getSuspendParameters())
                        .map(p -> p.getType().getName())
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
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(V1_8, ACC_SYNTHETIC | ACC_FINAL,
                internalName,
                null,
                SUPER_TYPE.getInternalName(),
                null);
        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);

        Type methodsFieldType = Type.getType(AbstractExecutableMethodsDefinition.MethodReference[].class);

        buildStaticInit(classWriter, methodsFieldType);
        buildConstructor(classWriter, methodsFieldType);
        methodDispatchWriter.buildDispatchMethod(classWriter);
        methodDispatchWriter.buildGetTargetMethodByIndex(classWriter);
        if (methodDispatchWriter.getDispatchTargets().size() > MIN_METHODS_TO_GENERATE_GET_METHOD) {
            buildGetMethod(classWriter);
        }

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }

        classWriter.visitEnd();

        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className, getOriginatingElements())) {
            outputStream.write(classWriter.toByteArray());
        }
    }

    private void buildStaticInit(ClassWriter classWriter, Type methodsFieldType) {
        GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_METHODS_REFERENCES, methodsFieldType.getDescriptor(), null, null);
        pushNewArray(staticInit, AbstractExecutableMethodsDefinition.MethodReference.class, methodDispatchWriter.getDispatchTargets().size());
        int i = 0;
        for (DispatchWriter.DispatchTarget dispatchTarget : methodDispatchWriter.getDispatchTargets()) {
            DispatchWriter.MethodDispatchTarget method = (DispatchWriter.MethodDispatchTarget) dispatchTarget;
            pushStoreInArray(staticInit, i++, methodDispatchWriter.getDispatchTargets().size(), () ->
                    pushNewMethodReference(
                            classWriter,
                            staticInit,
                            method.declaringType,
                            method.methodElement
                    )
            );
        }
        staticInit.putStatic(thisType, FIELD_METHODS_REFERENCES, methodsFieldType);
        staticInit.returnValue();
        staticInit.visitMaxs(DEFAULT_MAX_STACK, 1);
        staticInit.visitEnd();
    }

    private void buildConstructor(ClassWriter classWriter, Type methodsFieldType) {
        boolean includeInterceptedField = methodDispatchWriter.isHasInterceptedMethod();
        if (includeInterceptedField) {
            classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_INTERCEPTABLE, Type.getType(boolean.class).getDescriptor(), null, null);

            // Create default constructor call other one with 'false'
            GeneratorAdapter defaultConstructorWriter = startConstructor(classWriter);
            defaultConstructorWriter.loadThis();
            defaultConstructorWriter.push(false);
            defaultConstructorWriter.invokeConstructor(thisType, WITH_INTERCEPTED_CONSTRUCTOR);
            defaultConstructorWriter.returnValue();
            defaultConstructorWriter.visitMaxs(1, 1);
            defaultConstructorWriter.visitEnd();

            GeneratorAdapter withInterceptedConstructor = startConstructor(classWriter, boolean.class);
            withInterceptedConstructor.loadThis();
            withInterceptedConstructor.getStatic(thisType, FIELD_METHODS_REFERENCES, methodsFieldType);
            withInterceptedConstructor.invokeConstructor(SUPER_TYPE, SUPER_CONSTRUCTOR);
            withInterceptedConstructor.loadThis();
            withInterceptedConstructor.loadArg(0);
            withInterceptedConstructor.putField(thisType, FIELD_INTERCEPTABLE, Type.getType(boolean.class));
            withInterceptedConstructor.returnValue();
            withInterceptedConstructor.visitMaxs(1, 1);
            withInterceptedConstructor.visitEnd();
        } else {
            GeneratorAdapter constructorWriter = startConstructor(classWriter);
            constructorWriter.loadThis();
            constructorWriter.getStatic(thisType, FIELD_METHODS_REFERENCES, methodsFieldType);
            constructorWriter.invokeConstructor(SUPER_TYPE, SUPER_CONSTRUCTOR);
            constructorWriter.returnValue();
            constructorWriter.visitMaxs(1, 1);
            constructorWriter.visitEnd();
        }
    }

    private void buildGetMethod(ClassWriter classWriter) {
        GeneratorAdapter findMethod = new GeneratorAdapter(classWriter.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                GET_METHOD.getName(),
                GET_METHOD.getDescriptor(),
                null,
                null),
                ACC_PRIVATE | Opcodes.ACC_FINAL,
                GET_METHOD.getName(),
                GET_METHOD.getDescriptor()
        );
        findMethod.loadThis();
        findMethod.loadArg(0);
        findMethod.invokeVirtual(Type.getType(Object.class), new Method("hashCode", Type.INT_TYPE, new Type[]{}));

        Map<Integer, List<DispatchWriter.MethodDispatchTarget>> hashToMethods = new TreeMap<>();
        for (DispatchWriter.DispatchTarget dispatchTarget : methodDispatchWriter.getDispatchTargets()) {
            DispatchWriter.MethodDispatchTarget method = (DispatchWriter.MethodDispatchTarget) dispatchTarget;
            int hash = method.methodElement.getName().hashCode();
            hashToMethods.computeIfAbsent(hash, h -> new ArrayList<>()).add(method);
        }
        int[] hashCodeArray = hashToMethods.keySet().stream().mapToInt(i -> i).toArray();
        findMethod.tableSwitch(hashCodeArray, new TableSwitchGenerator() {
            @Override
            public void generateCase(int hashCode, Label end) {
                for (DispatchWriter.MethodDispatchTarget method : hashToMethods.get(hashCode)) {
                    int index = methodDispatchWriter.getDispatchTargets().indexOf(method);
                    if (index < 0) {
                        throw new IllegalStateException();
                    }
                    findMethod.loadThis();
                    findMethod.push(index);
                    findMethod.loadArg(0);
                    findMethod.loadArg(1);
                    findMethod.invokeVirtual(SUPER_TYPE, AT_INDEX_MATCHED_METHOD);
                    findMethod.push(true);
                    Label falseLabel = new Label();
                    findMethod.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, falseLabel);
                    findMethod.loadThis();
                    findMethod.push(index);
                    findMethod.invokeVirtual(SUPER_TYPE, GET_EXECUTABLE_AT_INDEX_METHOD);
                    findMethod.returnValue();
                    findMethod.visitLabel(falseLabel);
                }
                findMethod.goTo(end);
            }

            @Override
            public void generateDefault() {
            }
        });
        findMethod.push((String) null);
        findMethod.returnValue();
        findMethod.visitMaxs(DEFAULT_MAX_STACK, 1);
        findMethod.visitEnd();
    }

    private void pushNewMethodReference(ClassWriter classWriter,
                                        GeneratorAdapter staticInit,
                                        TypedElement declaringType,
                                        MethodElement methodElement) {
        staticInit.newInstance(Type.getType(AbstractExecutableMethodsDefinition.MethodReference.class));
        staticInit.dup();
        // 1: declaringType
        Type typeReference = JavaModelUtils.getTypeReference(declaringType.getType());
        staticInit.push(typeReference);
        // 2: annotationMetadata
        AnnotationMetadata annotationMetadata = methodElement.unwrapAnnotationMetadata();

        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataHierarchy hierarchy = (AnnotationMetadataHierarchy) annotationMetadata;
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

        pushAnnotationMetadata(classWriter, staticInit, annotationMetadata);
        // 3: methodName
        staticInit.push(methodElement.getName());
        // 4: return argument
        ClassElement genericReturnType = methodElement.getGenericReturnType();
        pushReturnTypeArgument(thisType, classWriter, staticInit, declaringType.getName(), genericReturnType, defaultsStorage, loadTypeMethods);
        // 5: arguments
        ParameterElement[] parameters = methodElement.getSuspendParameters();
        if (parameters.length == 0) {
            staticInit.visitInsn(ACONST_NULL);
        } else {
            pushBuildArgumentsForMethod(
                    typeReference.getClassName(),
                    thisType,
                    classWriter,
                    staticInit,
                    Arrays.asList(parameters),
                    defaultsStorage,
                    loadTypeMethods
            );
        }
        // 6: isAbstract
        staticInit.push(methodElement.isAbstract());
        // 7: isSuspend
        staticInit.push(methodElement.isSuspend());

        invokeConstructor(
                staticInit,
                AbstractExecutableMethodsDefinition.MethodReference.class,
                Class.class,
                AnnotationMetadata.class,
                String.class,
                Argument.class,
                Argument[].class,
                boolean.class,
                boolean.class);
    }

    private void pushAnnotationMetadata(ClassWriter classWriter, GeneratorAdapter staticInit, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            staticInit.push((String) null);
        } else if (annotationMetadata instanceof AnnotationMetadataReference) {
            AnnotationMetadataReference reference = (AnnotationMetadataReference) annotationMetadata;
            String className = reference.getClassName();
            staticInit.getStatic(getTypeReferenceForName(className), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                    thisType,
                    classWriter,
                    staticInit,
                    (AnnotationMetadataHierarchy) annotationMetadata,
                    defaultsStorage,
                    loadTypeMethods);
        } else if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    thisType,
                    classWriter,
                    staticInit,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    defaultsStorage,
                    loadTypeMethods);
        } else {
            throw new IllegalStateException("Unknown metadata: " + annotationMetadata);
        }
    }
}
