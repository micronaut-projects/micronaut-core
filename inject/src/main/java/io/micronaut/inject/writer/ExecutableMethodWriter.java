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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Writes out {@link io.micronaut.inject.ExecutableMethod} implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class ExecutableMethodWriter extends AbstractAnnotationMetadataWriter implements Opcodes {

    protected static final org.objectweb.asm.commons.Method METHOD_INVOKE_INTERNAL = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethod.class, "invokeInternal", Object.class, Object[].class));
    protected static final org.objectweb.asm.commons.Method METHOD_IS_ABSTRACT = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ExecutableMethod.class, "isAbstract"));
    protected static final org.objectweb.asm.commons.Method METHOD_IS_SUSPEND = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ExecutableMethod.class, "isSuspend"));
    protected static final Method METHOD_GET_TARGET = Method.getMethod("java.lang.reflect.Method resolveTargetMethod()");
    private static final Type TYPE_REFLECTION_UTILS = Type.getType(ReflectionUtils.class);
    private static final org.objectweb.asm.commons.Method METHOD_GET_REQUIRED_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getRequiredMethod", Class.class, String.class, Class[].class));
    private static final String FIELD_INTERCEPTABLE = "$interceptable";

    protected final Type methodType;

    private final ClassWriter classWriter;
    private final String className;
    private final String internalName;
    private final boolean isInterface;
    private final boolean isAbstract;
    private final boolean isSuspend;
    private final boolean isDefault;
    private final String interceptedProxyClassName;
    private final String interceptedProxyBridgeMethodName;

    /**
     * @param methodClassName      The method class name
     * @param isInterface          Whether is an interface
     * @param isAbstract           Whether the method is abstract
     * @param isDefault            Whether the method is a default method
     * @param isSuspend            Whether the method is Kotlin suspend function
     * @param originatingElements  The originating elements
     * @param annotationMetadata   The annotation metadata
     * @param interceptedProxyClassName        The intercepted proxy class name
     * @param interceptedProxyBridgeMethodName The intercepted proxy bridge method name
     */
    public ExecutableMethodWriter(
            String methodClassName,
            boolean isInterface,
            boolean isAbstract,
            boolean isDefault,
            boolean isSuspend,
            OriginatingElements originatingElements,
            AnnotationMetadata annotationMetadata,
            String interceptedProxyClassName,
            String interceptedProxyBridgeMethodName) {
        super(methodClassName, originatingElements, annotationMetadata, true);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.className = methodClassName;
        this.internalName = getInternalName(methodClassName);
        this.methodType = getObjectType(methodClassName);
        this.isInterface = isInterface;
        this.isAbstract = isAbstract;
        this.isDefault = isDefault;
        this.isSuspend = isSuspend;
        this.interceptedProxyClassName = interceptedProxyClassName;
        this.interceptedProxyBridgeMethodName = interceptedProxyBridgeMethodName;
    }

    /**
     * @return Is supports intercepted proxy.
     */
    public boolean isSupportsInterceptedProxy() {
        return interceptedProxyClassName != null;
    }

    /**
     * @return Is the method abstract.
     */
    public boolean isAbstract() {
        return isAbstract;
    }

    /**
     * @return Is the method in an interface.
     */
    public boolean isInterface() {
        return isInterface;
    }

    /**
     * @return Is the method a default method.
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * @return Is the method suspend.
     */
    public boolean isSuspend() {
        return isSuspend;
    }

    /**
     * @return The class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return The internal name
     */
    public String getInternalName() {
        return internalName;
    }

    /**
     * Write the method.
     *
     * @param declaringType              The declaring type
     * @param methodElement              The method element
     */
    public void visitMethod(TypedElement declaringType,
                            MethodElement methodElement) {
        String methodName = methodElement.getName();
        List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getSuspendParameters());
        Type declaringTypeObject = getTypeReference(declaringType);
        boolean hasArgs = !argumentTypes.isEmpty();

        classWriter.visit(V1_8, ACC_SYNTHETIC,
                internalName,
                null,
                Type.getInternalName(AbstractExecutableMethod.class),
                null);

        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);

        // initialize and write the annotation metadata
        if (!(annotationMetadata instanceof AnnotationMetadataReference)) {
            writeAnnotationMetadataStaticInitializer(classWriter);
        }
        writeGetAnnotationMetadataMethod(classWriter);

        MethodVisitor executorMethodConstructor;
        GeneratorAdapter constructorWriter;
        if (interceptedProxyBridgeMethodName != null) {
            // Create default constructor call other one with 'false'

            String descriptor = Type.getDescriptor(boolean.class);
            classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_INTERCEPTABLE, descriptor, null, null);

            GeneratorAdapter defaultConstructorWriter = new GeneratorAdapter(startConstructor(classWriter),
                    Opcodes.ACC_PUBLIC,
                    CONSTRUCTOR_NAME,
                    DESCRIPTOR_DEFAULT_CONSTRUCTOR);

            String executorMethodConstructorDescriptor = getConstructorDescriptor(boolean.class);
            executorMethodConstructor = startConstructor(classWriter, boolean.class);
            constructorWriter = new GeneratorAdapter(executorMethodConstructor,
                    Opcodes.ACC_PUBLIC,
                    CONSTRUCTOR_NAME,
                    executorMethodConstructorDescriptor);

            defaultConstructorWriter.loadThis();
            defaultConstructorWriter.push(false);
            defaultConstructorWriter.visitMethodInsn(INVOKESPECIAL, internalName, CONSTRUCTOR_NAME, executorMethodConstructorDescriptor, false);
            defaultConstructorWriter.visitInsn(RETURN);
            defaultConstructorWriter.visitMaxs(DEFAULT_MAX_STACK, 1);

            constructorWriter.loadThis();
            constructorWriter.loadArg(0);
            constructorWriter.putField(Type.getObjectType(internalName), FIELD_INTERCEPTABLE, Type.getType(boolean.class));
        } else {
            executorMethodConstructor = startConstructor(classWriter);
            constructorWriter = new GeneratorAdapter(executorMethodConstructor,
                    Opcodes.ACC_PUBLIC,
                    CONSTRUCTOR_NAME,
                    DESCRIPTOR_DEFAULT_CONSTRUCTOR);
        }

        // ALOAD 0
        constructorWriter.loadThis();

        // load 'this'
        constructorWriter.loadThis();

        // 1st argument: the declaring class
        constructorWriter.push(declaringTypeObject);

        // 2nd argument: the method name
        constructorWriter.push(methodName);

        // 3rd argument the generic return type
        ClassElement genericReturnType = methodElement.getGenericReturnType();
        if (genericReturnType.isPrimitive() && !genericReturnType.isArray()) {
            String constantName = genericReturnType.getName().toUpperCase(Locale.ENGLISH);

            // refer to constant for primitives
            Type type = Type.getType(Argument.class);
            constructorWriter.getStatic(type, constantName, type);

        } else {
            pushCreateArgument(
                    declaringType.getName(),
                    methodType,
                    classWriter,
                    constructorWriter,
                    genericReturnType.getName(),
                    genericReturnType,
                    genericReturnType.getAnnotationMetadata(),
                    genericReturnType.getTypeArguments(),
                    loadTypeMethods
            );
        }

        if (hasArgs) {
            // 4th argument: the generic types
            pushBuildArgumentsForMethod(
                    methodType.getClassName(),
                    methodType,
                    classWriter,
                    constructorWriter,
                    argumentTypes,
                    loadTypeMethods
            );

            for (ParameterElement pe : argumentTypes) {
                DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, pe.getAnnotationMetadata());
            }
            // now invoke super(..) if no arg constructor
            invokeConstructor(
                    executorMethodConstructor,
                    AbstractExecutableMethod.class,
                    Class.class,
                    String.class,
                    Argument.class,
                    Argument[].class);
        } else {
            invokeConstructor(
                    executorMethodConstructor,
                    AbstractExecutableMethod.class,
                    Class.class,
                    String.class,
                    Argument.class);
        }
        constructorWriter.visitInsn(RETURN);
        constructorWriter.visitMaxs(DEFAULT_MAX_STACK, 1);

        // add isAbstract method
        GeneratorAdapter isAbstractMethod = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC | ACC_FINAL,
                METHOD_IS_ABSTRACT.getName(),
                METHOD_IS_ABSTRACT.getDescriptor(),
                null,
                null),
                ACC_PUBLIC,
                METHOD_IS_ABSTRACT.getName(),
                METHOD_IS_ABSTRACT.getDescriptor()
        );

        isAbstractMethod.push(isAbstract());
        isAbstractMethod.returnValue();
        isAbstractMethod.visitMaxs(1, 1);
        isAbstractMethod.endMethod();

        // add isSuspend method
        GeneratorAdapter isSuspendMethod = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC | ACC_FINAL,
                METHOD_IS_SUSPEND.getName(),
                METHOD_IS_SUSPEND.getDescriptor(),
                null,
                null),
                ACC_PUBLIC,
                METHOD_IS_SUSPEND.getName(),
                METHOD_IS_SUSPEND.getDescriptor()
        );

        isSuspendMethod.push(isSuspend());
        isSuspendMethod.returnValue();
        isSuspendMethod.visitMaxs(1, 1);
        isSuspendMethod.endMethod();

        // invoke the methods with the passed arguments
        String invokeDescriptor = METHOD_INVOKE_INTERNAL.getDescriptor();
        String invokeInternalName = METHOD_INVOKE_INTERNAL.getName();
        GeneratorAdapter invokeMethod = new GeneratorAdapter(classWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                invokeInternalName,
                invokeDescriptor,
                null,
                null),
                ACC_PUBLIC,
                invokeInternalName,
                invokeDescriptor
        );

        ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
        buildInvokeMethod(declaringTypeObject, methodName, returnType, argumentTypes, invokeMethod);

        buildResolveTargetMethod(methodName, declaringTypeObject, hasArgs, argumentTypes);

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className, getOriginatingElements())) {
            outputStream.write(classWriter.toByteArray());
        }
    }

    @NonNull
    @Override
    protected final GeneratorAdapter beginAnnotationMetadataMethod(ClassWriter classWriter) {
        return startProtectedMethod(classWriter, "resolveAnnotationMetadata", AnnotationMetadata.class.getName());
    }

    /**
     * @param declaringTypeObject The declaring object type
     * @param methodName          The method name
     * @param returnType          The return type
     * @param argumentTypes       The argument types
     * @param invokeMethodVisitor The invoke method visitor
     */
    protected void buildInvokeMethod(
            Type declaringTypeObject,
            String methodName,
            ClassElement returnType,
            Collection<ParameterElement> argumentTypes,
            GeneratorAdapter invokeMethodVisitor) {
        Type returnTypeObject = getTypeReference(returnType);

        // load this
        invokeMethodVisitor.visitVarInsn(ALOAD, 1);
        // duplicate target
        invokeMethodVisitor.dup();

        String methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
        if (interceptedProxyClassName != null) {
            Label invokeTargetBlock = new Label();

            Type interceptedProxyType = getObjectType(interceptedProxyClassName);

            // load this.$interceptable field value
            invokeMethodVisitor.loadThis();
            invokeMethodVisitor.getField(Type.getObjectType(internalName), FIELD_INTERCEPTABLE, Type.getType(boolean.class));
            // check if it equals true
            invokeMethodVisitor.push(true);
            invokeMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, invokeTargetBlock);

            // target instanceOf intercepted proxy
            invokeMethodVisitor.loadArg(0);
            invokeMethodVisitor.instanceOf(interceptedProxyType);
            // check if instanceOf
            invokeMethodVisitor.push(true);
            invokeMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, invokeTargetBlock);

            pushCastToType(invokeMethodVisitor, interceptedProxyType);

            // load arguments
            Iterator<ParameterElement> iterator = argumentTypes.iterator();
            for (int i = 0; i < argumentTypes.size(); i++) {
                invokeMethodVisitor.loadArg(1);
                invokeMethodVisitor.push(i);
                invokeMethodVisitor.visitInsn(AALOAD);

                pushCastToType(invokeMethodVisitor, iterator.next());
            }

            invokeMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                    interceptedProxyType.getInternalName(), interceptedProxyBridgeMethodName,
                    methodDescriptor, false);

            if (returnTypeObject.equals(Type.VOID_TYPE)) {
                invokeMethodVisitor.visitInsn(ACONST_NULL);
            } else {
                pushBoxPrimitiveIfNecessary(returnType, invokeMethodVisitor);
            }
            invokeMethodVisitor.visitInsn(ARETURN);

            invokeMethodVisitor.visitLabel(invokeTargetBlock);

            // remove parent
            invokeMethodVisitor.pop();
        }

        pushCastToType(invokeMethodVisitor, declaringTypeObject);
        boolean hasArgs = !argumentTypes.isEmpty();
        if (hasArgs) {
            int argCount = argumentTypes.size();
            Iterator<ParameterElement> argIterator = argumentTypes.iterator();
            for (int i = 0; i < argCount; i++) {
                invokeMethodVisitor.visitVarInsn(ALOAD, 2);
                invokeMethodVisitor.push(i);
                invokeMethodVisitor.visitInsn(AALOAD);
                // cast the return value to the correct type
                pushCastToType(invokeMethodVisitor, argIterator.next());
            }
        }

        invokeMethodVisitor.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                declaringTypeObject.getInternalName(), methodName,
                methodDescriptor, isInterface);

        if (returnTypeObject.equals(Type.VOID_TYPE)) {
            invokeMethodVisitor.visitInsn(ACONST_NULL);
        } else {
            pushBoxPrimitiveIfNecessary(returnType, invokeMethodVisitor);
        }
        invokeMethodVisitor.visitInsn(ARETURN);

        invokeMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 1);
        invokeMethodVisitor.visitEnd();
    }

    private void buildResolveTargetMethod(String methodName, Type declaringTypeObject, boolean hasArgs, Collection<ParameterElement> argumentTypeClasses) {
        String targetMethodInternalName = METHOD_GET_TARGET.getName();
        String targetMethodDescriptor = METHOD_GET_TARGET.getDescriptor();
        GeneratorAdapter getTargetMethod = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC | ACC_FINAL,
                targetMethodInternalName,
                targetMethodDescriptor,
                null,
                null),
                ACC_PUBLIC | ACC_FINAL,
                targetMethodInternalName,
                targetMethodDescriptor
        );

        getTargetMethod.push(declaringTypeObject);
        getTargetMethod.push(methodName);
        if (hasArgs) {
            int len = argumentTypeClasses.size();
            Iterator<ParameterElement> iter = argumentTypeClasses.iterator();
            pushNewArray(getTargetMethod, Class.class, len);
            for (int i = 0; i < len; i++) {
                ParameterElement type = iter.next();
                pushStoreInArray(
                        getTargetMethod,
                        i,
                        len,
                        () -> getTargetMethod.push(getTypeReference(type))
                );

            }
        } else {
            getTargetMethod.getStatic(TYPE_REFLECTION_UTILS, "EMPTY_CLASS_ARRAY", Type.getType(Class[].class));
        }
        getTargetMethod.invokeStatic(TYPE_REFLECTION_UTILS, METHOD_GET_REQUIRED_METHOD);
        getTargetMethod.returnValue();
        getTargetMethod.visitMaxs(1, 1);
        getTargetMethod.endMethod();
    }
}
