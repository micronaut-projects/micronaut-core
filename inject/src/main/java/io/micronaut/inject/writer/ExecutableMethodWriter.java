/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.annotation.Nonnull;
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

    /**
     * Constant for parent field.
     */
    public static final String FIELD_PARENT = "$parent";

    protected static final org.objectweb.asm.commons.Method METHOD_INVOKE_INTERNAL = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethod.class, "invokeInternal", Object.class, Object[].class));
    protected static final org.objectweb.asm.commons.Method METHOD_IS_ABSTRACT = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ExecutableMethod.class, "isAbstract"));
    protected static final org.objectweb.asm.commons.Method METHOD_IS_SUSPEND = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ExecutableMethod.class, "isSuspend"));
    protected static final Method METHOD_GET_TARGET = Method.getMethod("java.lang.reflect.Method resolveTargetMethod()");
    private  static final Type TYPE_REFLECTION_UTILS = Type.getType(ReflectionUtils.class);
    private static final org.objectweb.asm.commons.Method METHOD_GET_REQUIRED_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getRequiredMethod", Class.class, String.class, Class[].class));

    protected final Type methodType;

    private final ClassWriter classWriter;
    private final String className;
    private final String internalName;
    private final String beanFullClassName;
    private final String methodProxyShortName;
    private final boolean isInterface;
    private final boolean isAbstract;
    private final boolean isSuspend;
    private final boolean isDefault;
    private String outerClassName = null;
    private boolean isStatic = false;

    /**
     * @param beanFullClassName    The bean full class name
     * @param methodClassName      The method class name
     * @param methodProxyShortName The method proxy short name
     * @param isInterface          Whether is an interface
     * @param isDefault            Whether the method is a default method
     * @param isSuspend            Whether the method is Kotlin suspend function
     * @param annotationMetadata   The annotation metadata
     */
    public ExecutableMethodWriter(
            String beanFullClassName,
            String methodClassName,
            String methodProxyShortName,
            boolean isInterface,
            boolean isDefault,
            boolean isSuspend,
            AnnotationMetadata annotationMetadata) {
        super(methodClassName, annotationMetadata, true);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.beanFullClassName = beanFullClassName;
        this.methodProxyShortName = methodProxyShortName;
        this.className = methodClassName;
        this.internalName = getInternalName(methodClassName);
        this.methodType = getObjectType(methodClassName);
        this.isInterface = isInterface;
        this.isAbstract = !isInterface || !isDefault;
        this.isDefault = isDefault;
        this.isSuspend = isSuspend;
    }


    /**
     * @param beanFullClassName    The bean full class name
     * @param methodClassName      The method class name
     * @param methodProxyShortName The method proxy short name
     * @param isInterface          Whether is an interface
     * @param isAbstract           Whether the method is abstract
     * @param isDefault            Whether the method is a default method
     * @param isSuspend            Whether the method is Kotlin suspend function
     * @param annotationMetadata   The annotation metadata
     */
    public ExecutableMethodWriter(
            String beanFullClassName,
            String methodClassName,
            String methodProxyShortName,
            boolean isInterface,
            boolean isAbstract,
            boolean isDefault,
            boolean isSuspend,
            AnnotationMetadata annotationMetadata) {
        super(methodClassName, annotationMetadata, true);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.beanFullClassName = beanFullClassName;
        this.methodProxyShortName = methodProxyShortName;
        this.className = methodClassName;
        this.internalName = getInternalName(methodClassName);
        this.methodType = getObjectType(methodClassName);
        this.isInterface = isInterface;
        this.isAbstract = isAbstract;
        this.isDefault = isDefault;
        this.isSuspend = isSuspend;
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
     * @param outerName        The outer name
     * @param outerClassWriter The outer class writer
     */
    public void makeInner(String outerName, ClassWriter outerClassWriter) {
        outerClassWriter.visitInnerClass(internalName, getInternalName(outerName), methodProxyShortName.substring(1), 0);
        classWriter.visitOuterClass(getInternalName(outerName), null, null);
        if (!isStatic) {

            classWriter.visitField(ACC_PRIVATE | ACC_FINAL, FIELD_PARENT, getTypeDescriptor(outerName), null, null);
        }
        this.outerClassName = outerName;
    }

    /**
     * Write the method.
     *  @param declaringType              The declaring type
     * @param returnType                 The return type
     * @param genericReturnType          The generic return type
     * @param returnTypeGenericTypes     The return type generics
     * @param methodName                 The method name
     * @param argumentTypes              The argument types
     * @param genericArgumentTypes       The generic argument types
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types
     */
    public void visitMethod(Object declaringType,
                            Object returnType,
                            Object genericReturnType,
                            Map<String, Object> returnTypeGenericTypes,
                            String methodName,
                            Map<String, Object> argumentTypes,
                            Map<String, Object> genericArgumentTypes,
                            Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                            Map<String, Map<String, Object>> genericTypes) {
        Type declaringTypeObject = getTypeReference(declaringType);
        boolean hasArgs = !argumentTypes.isEmpty();
        Collection<Object> argumentTypeClasses = hasArgs ? argumentTypes.values() : Collections.emptyList();

        int modifiers = isStatic ? ACC_SYNTHETIC | ACC_STATIC : ACC_SYNTHETIC;
        classWriter.visit(V1_8, modifiers,
            internalName,
            null,
            Type.getInternalName(AbstractExecutableMethod.class),
            null);

        // initialize and write the annotation metadata
        if (!(annotationMetadata instanceof AnnotationMetadataReference)) {
            writeAnnotationMetadataStaticInitializer(classWriter);
        }
        writeGetAnnotationMetadataMethod(classWriter);

        MethodVisitor executorMethodConstructor;
        GeneratorAdapter constructorWriter;

        boolean hasOuter = outerClassName != null && !isStatic;
        String constructorDescriptor;
        if (hasOuter) {
            executorMethodConstructor = startConstructor(classWriter, outerClassName);
            constructorDescriptor = getConstructorDescriptor(outerClassName);
        } else {
            executorMethodConstructor = startConstructor(classWriter);
            constructorDescriptor = DESCRIPTOR_DEFAULT_CONSTRUCTOR;
        }
        constructorWriter = new GeneratorAdapter(executorMethodConstructor,
            Opcodes.ACC_PUBLIC,
            CONSTRUCTOR_NAME,
            constructorDescriptor);

        if (hasOuter) {
            constructorWriter.loadThis();
            constructorWriter.loadArg(0);
            constructorWriter.putField(methodType, FIELD_PARENT, getObjectType(outerClassName));
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
        if (genericReturnType instanceof Class && ((Class) genericReturnType).isPrimitive()) {
            Class javaType = (Class) genericReturnType;
            String constantName = javaType.getName().toUpperCase(Locale.ENGLISH);

            // refer to constant for primitives
            Type type = Type.getType(Argument.class);
            constructorWriter.getStatic(type, constantName, type);

        } else {
            // Argument.of(genericReturnType, returnTypeGenericTypes)
            buildArgumentWithGenerics(
                constructorWriter,
                methodName,
                Collections.singletonMap(genericReturnType, returnTypeGenericTypes)
            );
        }

        if (hasArgs) {
            // 4th argument: the generic types
            pushBuildArgumentsForMethod(
                getTypeReferenceForName(getClassName()),
                classWriter,
                constructorWriter,
                genericArgumentTypes,
                argumentAnnotationMetadata,
                genericTypes,
                loadTypeMethods);

            for (AnnotationMetadata value : argumentAnnotationMetadata.values()) {
                DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, value);
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

        buildInvokeMethod(declaringTypeObject, methodName, returnType, argumentTypeClasses, invokeMethod);

        buildResolveTargetMethod(methodName, declaringTypeObject, hasArgs, argumentTypeClasses);

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }
    }

    /**
     * @param parentInternalName The parent internal name
     * @param classWriter        The current class writer
     */
    public void makeStaticInner(String parentInternalName, ClassWriter classWriter) {
        this.isStatic = true;
        makeInner(parentInternalName, classWriter);
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className)) {
            outputStream.write(classWriter.toByteArray());
        }
    }

    @Nonnull
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
            Object returnType,
            Collection<Object> argumentTypes,
            GeneratorAdapter invokeMethodVisitor) {
        Type returnTypeObject = getTypeReference(returnType);
        invokeMethodVisitor.visitVarInsn(ALOAD, 1);
        pushCastToType(invokeMethodVisitor, beanFullClassName);
        boolean hasArgs = !argumentTypes.isEmpty();
        String methodDescriptor;
        if (hasArgs) {
            methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
            int argCount = argumentTypes.size();
            Iterator<Object> argIterator = argumentTypes.iterator();
            for (int i = 0; i < argCount; i++) {
                invokeMethodVisitor.visitVarInsn(ALOAD, 2);
                invokeMethodVisitor.push(i);
                invokeMethodVisitor.visitInsn(AALOAD);
                // cast the return value to the correct type
                pushCastToType(invokeMethodVisitor, argIterator.next());
            }
        } else {
            methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
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

    private void buildResolveTargetMethod(String methodName, Type declaringTypeObject, boolean hasArgs, Collection<Object> argumentTypeClasses) {
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
            Iterator<Object> iter = argumentTypeClasses.iterator();
            pushNewArray(getTargetMethod, Class.class, len);
            for (int i = 0; i < len; i++) {
                Object type = iter.next();
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
