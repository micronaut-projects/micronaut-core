/*
 * Copyright 2017-2018 original authors
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

import static io.micronaut.inject.writer.BeanDefinitionWriter.buildArgumentWithGenerics;
import static io.micronaut.inject.writer.BeanDefinitionWriter.pushBuildArgumentsForMethod;

import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

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
public class ExecutableMethodWriter extends AbstractAnnotationMetadataWriter implements Opcodes {

    /**
     * Constant for parent field.
     */
    public static final String FIELD_PARENT = "$parent";

    protected static final org.objectweb.asm.commons.Method METHOD_INVOKE_INTERNAL = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethod.class, "invokeInternal", Object.class, Object[].class));
    protected final Type methodType;

    private final ClassWriter classWriter;
    private final String className;
    private final String internalName;
    private final String beanFullClassName;
    private final String methodProxyShortName;
    private final boolean isInterface;
    private String outerClassName = null;
    private boolean isStatic = false;

    /**
     * @param beanFullClassName    The bean full class name
     * @param methodClassName      The method class name
     * @param methodProxyShortName The method proxy short name
     * @param isInterface          Whether is an interface
     * @param annotationMetadata   The annotation metadata
     */
    public ExecutableMethodWriter(String beanFullClassName, String methodClassName, String methodProxyShortName, boolean isInterface, AnnotationMetadata annotationMetadata) {
        super(methodClassName, annotationMetadata);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.beanFullClassName = beanFullClassName;
        this.methodProxyShortName = methodProxyShortName;
        this.className = methodClassName;
        this.internalName = getInternalName(methodClassName);
        this.methodType = getObjectType(methodClassName);
        this.isInterface = isInterface;
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
     *
     * @param declaringType              The declaring type
     * @param returnType                 The return type
     * @param genericReturnType          The generic return type
     * @param returnTypeGenericTypes     The return type generics
     * @param methodName                 The method name
     * @param argumentTypes              The argument types
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types
     */
    public void visitMethod(Object declaringType,
                            Object returnType,
                            Object genericReturnType,
                            Map<String, Object> returnTypeGenericTypes,
                            String methodName,
                            Map<String, Object> argumentTypes,
                            Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                            Map<String, Map<String, Object>> genericTypes) {
        Type declaringTypeObject = getTypeReference(declaringType);
        boolean hasArgs = !argumentTypes.isEmpty();
        Collection<Object> argumentTypeClasses = hasArgs ? argumentTypes.values() : Collections.emptyList();

        int modifiers = isStatic ? ACC_PUBLIC | ACC_STATIC : ACC_PUBLIC;
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
                constructorWriter,
                argumentTypes,
                argumentAnnotationMetadata,
                genericTypes
            );
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
    }

    /**
     * @param declaringTypeObject The declaring object type
     * @param methodName          The method name
     * @param returnType          The return type
     * @param argumentTypes       The argument types
     * @param invokeMethodVisitor The invoke method visitor
     */
    protected void buildInvokeMethod(Type declaringTypeObject, String methodName, Object returnType, Collection<Object> argumentTypes, GeneratorAdapter invokeMethodVisitor) {
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
        AnnotationMetadataWriter annotationMetadataWriter = getAnnotationMetadataWriter();
        if (annotationMetadataWriter != null) {
            annotationMetadataWriter.accept(classWriterOutputVisitor);
        }
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(className)) {
            outputStream.write(classWriter.toByteArray());
        }
    }

    @Nonnull
    @Override
    protected final GeneratorAdapter beginAnnotationMetadataMethod(ClassWriter classWriter) {
        return startProtectedMethod(classWriter, "resolveAnnotationMetadata", AnnotationMetadata.class.getName());
    }
}
