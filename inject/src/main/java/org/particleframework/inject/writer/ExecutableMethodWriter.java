/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.writer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.particleframework.context.AbstractExecutableMethod;
import org.particleframework.core.reflect.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;

import static org.particleframework.inject.writer.BeanDefinitionWriter.*;

/**
 * Writes out {@link org.particleframework.inject.ExecutableMethod} implementations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ExecutableMethodWriter extends AbstractClassFileWriter implements Opcodes
{
    public static final String FIELD_PARENT = "$parent";
    public static final org.objectweb.asm.commons.Method METHOD_INVOKE_INTERNAL = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getDeclaredMethod(AbstractExecutableMethod.class, "invokeInternal", Object.class, Object[].class).orElseThrow(() -> new IllegalStateException("AbstractExecutableMethod.invokeInternal(..) method not found. Incompatible version of Particle on the classpath?"))
    );
    private final ClassWriter classWriter;
    private final String className;
    private final String internalName;
    private final String beanFullClassName;
    private final String methodProxyShortName;
    protected final Type methodType;
    private String outerClassName = null;

    public ExecutableMethodWriter(String beanFullClassName, String methodClassName, String methodProxyShortName) {
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.beanFullClassName = beanFullClassName;
        this.methodProxyShortName = methodProxyShortName;
        this.className = methodClassName;
        this.internalName = getInternalName(methodClassName);
        this.methodType = getObjectType(methodClassName);
    }

    public String getClassName() {
        return className;
    }

    public ClassWriter getClassWriter() {
        return classWriter;
    }

    public String getInternalName() {
        return internalName;
    }

    public void makeInner(String outerName, ClassWriter outerClassWriter) {
        outerClassWriter.visitInnerClass(internalName, getInternalName(outerName), methodProxyShortName.substring(1), 0);
        classWriter.visitOuterClass(getInternalName(outerName), null, null);
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL, FIELD_PARENT, getTypeDescriptor(outerName) , null, null);
        this.outerClassName = outerName;
    }

    public void visitMethod(Object declaringType,
                            Object returnType,
                            List<Object> returnTypeGenericTypes,
                            String methodName,
                            Map<String, Object> argumentTypes,
                            Map<String, Object> qualifierTypes,
                            Map<String, List<Object>> genericTypes) {
        Type declaringTypeObject = getTypeReference(declaringType);

        classWriter.visit(V1_8, ACC_PUBLIC,
                internalName,
                null,
                Type.getInternalName(AbstractExecutableMethod.class),
                null);

        MethodVisitor executorMethodConstructor;
        GeneratorAdapter generatorAdapter;

        boolean hasOuter = outerClassName != null;
        String constructorDescriptor;
        if(hasOuter) {
            executorMethodConstructor = startConstructor(classWriter, outerClassName);
            constructorDescriptor = getConstructorDescriptor(outerClassName);
        }
        else {
            executorMethodConstructor = startConstructor(classWriter);
            constructorDescriptor = DESCRIPTOR_DEFAULT_CONSTRUCTOR;
        }
        generatorAdapter = new GeneratorAdapter(executorMethodConstructor,
                Opcodes.ACC_PUBLIC,
                CONSTRUCTOR_NAME,
                constructorDescriptor);

        if(hasOuter) {
            generatorAdapter.loadThis();
            generatorAdapter.loadArg(0);
            generatorAdapter.putField(methodType, FIELD_PARENT, getObjectType(outerClassName));
        }

        // ALOAD 0
        generatorAdapter.loadThis();

        // First constructor argument: The factory method
        boolean hasArgs = !argumentTypes.isEmpty();
        Collection<Object> argumentTypeClasses = hasArgs ? argumentTypes.values() : Collections.emptyList();
        // load 'this'
        generatorAdapter.loadThis();

        // 1st argument Class.getMethod(..)
        pushGetMethodFromTypeCall(executorMethodConstructor, declaringTypeObject, methodName, argumentTypeClasses);

        // 2nd argument the return types
        pushNewArrayOfTypes(executorMethodConstructor, returnTypeGenericTypes);

        if (hasArgs) {
            // 3rd Argument: Create a call to createMap from an argument types
            pushCreateMapCall(executorMethodConstructor, argumentTypes);

            // 4th Argument: Create a call to createMap from qualifier types
            if (qualifierTypes != null) {
                pushCreateMapCall(executorMethodConstructor, qualifierTypes);
            } else {
                executorMethodConstructor.visitInsn(ACONST_NULL);
            }

            // 5th Argument: Create a call to createMap from generic types
            if (genericTypes != null) {
                pushCreateGenericsMapCall(executorMethodConstructor, genericTypes);
            } else {
                executorMethodConstructor.visitInsn(ACONST_NULL);
            }
            // now invoke super(..) if no arg constructor
            invokeConstructor(executorMethodConstructor, AbstractExecutableMethod.class, Method.class, Class[].class, Map.class, Map.class, Map.class);
        } else {
            invokeConstructor(executorMethodConstructor, AbstractExecutableMethod.class, Method.class, Class[].class);
        }
        generatorAdapter.visitInsn(RETURN);
        generatorAdapter.visitMaxs(BeanDefinitionWriter.DEFAULT_MAX_STACK, 1);

        // invoke the methods with the passed arguments
        String invokeDescriptor = METHOD_INVOKE_INTERNAL.getDescriptor();
        MethodVisitor invokeMethod = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC,
                "invokeInternal",
                invokeDescriptor,
                null,
                null);

        buildInvokeMethod(declaringTypeObject, methodName, returnType, argumentTypeClasses, invokeMethod);
    }

    protected void buildInvokeMethod(Type declaringTypeObject, String methodName, Object returnType, Collection<Object> argumentTypes, MethodVisitor invokeMethodVisitor) {
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
                pushIntegerConstant(invokeMethodVisitor, i);
                invokeMethodVisitor.visitInsn(AALOAD);
                // cast the return value to the correct type
                pushCastToType(invokeMethodVisitor, argIterator.next());
            }
        } else {
            methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
        }
        invokeMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                declaringTypeObject.getInternalName(), methodName,
                methodDescriptor, false);

        if (returnTypeObject.equals(Type.VOID_TYPE)) {
            invokeMethodVisitor.visitInsn(ACONST_NULL);
        } else {
            pushBoxPrimitiveIfNecessary(returnType, invokeMethodVisitor);
        }
        invokeMethodVisitor.visitInsn(ARETURN);

        invokeMethodVisitor.visitMaxs(BeanDefinitionWriter.DEFAULT_MAX_STACK, 1);
        invokeMethodVisitor.visitEnd();
    }
}
