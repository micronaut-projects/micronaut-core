/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.KotlinParameterElement;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.Map;

/**
 * Switch based dispatch writer.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public final class DispatchWriter extends AbstractClassFileWriter implements Opcodes {

    private static final Method DISPATCH_METHOD = new Method("dispatch", getMethodDescriptor(Object.class, Arrays.asList(int.class, Object.class, Object[].class)));

    private static final Method DISPATCH_ONE_METHOD = new Method("dispatchOne", getMethodDescriptor(Object.class, Arrays.asList(int.class, Object.class, Object.class)));

    private static final Method GET_TARGET_METHOD = new Method("getTargetMethodByIndex", getMethodDescriptor(java.lang.reflect.Method.class, Collections.singletonList(int.class)));

    private static final Method GET_ACCESSIBLE_TARGET_METHOD = new Method("getAccessibleTargetMethodByIndex", getMethodDescriptor(java.lang.reflect.Method.class, Collections.singletonList(int.class)));

    private static final Method UNKNOWN_DISPATCH_AT_INDEX = new Method("unknownDispatchAtIndexException", getMethodDescriptor(RuntimeException.class, Collections.singletonList(int.class)));

    private static final String FIELD_INTERCEPTABLE = "$interceptable";

    private static final Type TYPE_REFLECTION_UTILS = Type.getType(ReflectionUtils.class);

    private static final org.objectweb.asm.commons.Method METHOD_GET_REQUIRED_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getRequiredMethod", Class.class, String.class, Class[].class));

    private static final org.objectweb.asm.commons.Method METHOD_INVOKE_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "invokeMethod", Object.class, java.lang.reflect.Method.class, Object[].class));

    private static final org.objectweb.asm.commons.Method METHOD_GET_FIELD_VALUE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getField", Class.class, String.class, Object.class));

    private static final org.objectweb.asm.commons.Method METHOD_SET_FIELD_VALUE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "setField", Class.class, String.class, Object.class, Object.class));

    private final List<DispatchTarget> dispatchTargets = new ArrayList<>();
    private final Type thisType;

    private final Type dispatchSuperType;

    private boolean hasInterceptedMethod;

    public DispatchWriter(Type thisType) {
        this(thisType, ExecutableMethodsDefinitionWriter.SUPER_TYPE);
    }

    public DispatchWriter(Type thisType, Type dispatchSuperType) {
        super();
        this.thisType = thisType;
        this.dispatchSuperType = dispatchSuperType;
    }

    /**
     * Adds new set field dispatch target.
     *
     * @param beanField The field
     * @return the target index
     */
    public int addSetField(FieldElement beanField) {
        return addDispatchTarget(new FieldSetDispatchTarget(beanField));
    }

    /**
     * Adds new get field dispatch target.
     *
     * @param beanField The field
     * @return the target index
     */
    public int addGetField(FieldElement beanField) {
        return addDispatchTarget(new FieldGetDispatchTarget(beanField));
    }

    /**
     * Adds new method dispatch target.
     *
     * @param declaringType The declaring type
     * @param methodElement The method element
     * @return the target index
     */
    public int addMethod(TypedElement declaringType, MethodElement methodElement) {
        return addMethod(declaringType, methodElement, false);
    }

    /**
     * Adds new method dispatch target.
     *
     * @param declaringType  The declaring type
     * @param methodElement  The method element
     * @param useOneDispatch If method should be dispatched using "dispatchOne"
     * @return the target index
     */
    public int addMethod(TypedElement declaringType, MethodElement methodElement, boolean useOneDispatch) {
        return addDispatchTarget(new MethodDispatchTarget(dispatchSuperType, declaringType, methodElement, useOneDispatch, !useOneDispatch));
    }

    /**
     * Adds new interceptable method dispatch target.
     *
     * @param declaringType                    The declaring type
     * @param methodElement                    The method element
     * @param interceptedProxyClassName        The interceptedProxyClassName
     * @param interceptedProxyBridgeMethodName The interceptedProxyBridgeMethodName
     * @return the target index
     */
    public int addInterceptedMethod(TypedElement declaringType,
                                    MethodElement methodElement,
                                    String interceptedProxyClassName,
                                    String interceptedProxyBridgeMethodName) {
        hasInterceptedMethod = true;
        return addDispatchTarget(new InterceptableMethodDispatchTarget(
                dispatchSuperType,
                declaringType,
                methodElement,
                interceptedProxyClassName,
                interceptedProxyBridgeMethodName,
                thisType)
        );
    }

    /**
     * Adds new custom dispatch target.
     *
     * @param dispatchTarget The dispatch target implementation
     * @return the target index
     */
    public int addDispatchTarget(DispatchTarget dispatchTarget) {
        dispatchTargets.add(dispatchTarget);
        return dispatchTargets.size() - 1;
    }

    /**
     * Build dispatch method if needed.
     *
     * @param classWriter The classwriter
     */
    public void buildDispatchMethod(ClassWriter classWriter) {
        int[] cases = dispatchTargets.stream()
                .filter(DispatchTarget::supportsDispatchMulti)
                .mapToInt(dispatchTargets::indexOf)
                .toArray();
        if (cases.length == 0) {
            return;
        }
        GeneratorAdapter dispatchMethod = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PROTECTED | Opcodes.ACC_FINAL,
                DISPATCH_METHOD.getName(),
                DISPATCH_METHOD.getDescriptor(),
                null,
                null),
                ACC_PROTECTED | Opcodes.ACC_FINAL,
                DISPATCH_METHOD.getName(),
                DISPATCH_METHOD.getDescriptor()
        );
        dispatchMethod.loadArg(0);
        dispatchMethod.tableSwitch(cases, new TableSwitchGenerator() {
            @Override
            public void generateCase(int key, Label end) {
                DispatchTarget method = dispatchTargets.get(key);
                method.writeDispatchMulti(dispatchMethod, key);
                dispatchMethod.returnValue();
            }

            @Override
            public void generateDefault() {
                dispatchMethod.loadThis();
                dispatchMethod.loadArg(0);
                dispatchMethod.invokeVirtual(thisType, UNKNOWN_DISPATCH_AT_INDEX);
                dispatchMethod.throwException();
            }
        }, true);
        dispatchMethod.visitMaxs(DEFAULT_MAX_STACK, 1);
        dispatchMethod.visitEnd();
    }

    /**
     * Build dispatch one method if needed.
     *
     * @param classWriter The classwriter
     */
    public void buildDispatchOneMethod(ClassWriter classWriter) {
        int[] cases = dispatchTargets.stream()
                .filter(DispatchTarget::supportsDispatchOne)
                .mapToInt(dispatchTargets::indexOf)
                .toArray();
        if (cases.length == 0) {
            return;
        }
        GeneratorAdapter dispatchMethod = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PROTECTED | ACC_FINAL,
                DISPATCH_ONE_METHOD.getName(),
                DISPATCH_ONE_METHOD.getDescriptor(),
                null,
                null),
                ACC_PROTECTED | ACC_FINAL,
                DISPATCH_ONE_METHOD.getName(),
                DISPATCH_ONE_METHOD.getDescriptor()
        );
        dispatchMethod.loadArg(0);
        Map<String, DispatchTargetState> stateMap = new HashMap<>();
        dispatchMethod.tableSwitch(cases, new TableSwitchGenerator() {
            @Override
            public void generateCase(int key, Label end) {
                DispatchTarget method = dispatchTargets.get(key);
                if (method.writeDispatchOne(dispatchMethod, key, stateMap)) {
                    dispatchMethod.returnValue();
                }
            }

            @Override
            public void generateDefault() {
                dispatchMethod.loadThis();
                dispatchMethod.loadArg(0);
                dispatchMethod.invokeVirtual(thisType, UNKNOWN_DISPATCH_AT_INDEX);
                dispatchMethod.throwException();
            }
        }, true);
        for (DispatchTargetState state : stateMap.values()) {
            state.complete(dispatchMethod);
        }
        dispatchMethod.visitMaxs(DEFAULT_MAX_STACK, 1);
        dispatchMethod.visitEnd();
    }

    /**
     * Build get target method by index method if needed.
     *
     * @param classWriter The classwriter
     */
    public void buildGetTargetMethodByIndex(ClassWriter classWriter) {
        GeneratorAdapter getTargetMethodByIndex = new GeneratorAdapter(classWriter.visitMethod(
                Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL,
                GET_TARGET_METHOD.getName(),
                GET_TARGET_METHOD.getDescriptor(),
                null,
                null),
                ACC_PROTECTED | Opcodes.ACC_FINAL,
                GET_TARGET_METHOD.getName(),
                GET_TARGET_METHOD.getDescriptor()
        );
        getTargetMethodByIndex.loadArg(0);
        int[] cases = dispatchTargets.stream()
                .filter(MethodDispatchTarget.class::isInstance)
                .mapToInt(dispatchTargets::indexOf)
                .toArray();
        getTargetMethodByIndex.tableSwitch(cases, new TableSwitchGenerator() {
            @Override
            public void generateCase(int key, Label end) {
                MethodDispatchTarget method = (MethodDispatchTarget) dispatchTargets.get(key);
                TypedElement declaringType = method.declaringType;
                Type declaringTypeObject = JavaModelUtils.getTypeReference(declaringType);
                MethodElement methodElement = method.methodElement;
                pushTypeUtilsGetRequiredMethod(getTargetMethodByIndex, declaringTypeObject, methodElement);
                getTargetMethodByIndex.returnValue();
            }

            @Override
            public void generateDefault() {
                getTargetMethodByIndex.loadThis();
                getTargetMethodByIndex.loadArg(0);
                getTargetMethodByIndex.invokeVirtual(thisType, UNKNOWN_DISPATCH_AT_INDEX);
                getTargetMethodByIndex.throwException();
            }
        }, true);
        getTargetMethodByIndex.visitMaxs(DEFAULT_MAX_STACK, 1);
        getTargetMethodByIndex.visitEnd();
    }

    public static void pushTypeUtilsGetRequiredMethod(GeneratorAdapter builder, Type declaringTypeObject, MethodElement methodElement) {
        List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getSuspendParameters());

        builder.push(declaringTypeObject);
        builder.push(methodElement.getName());
        if (!argumentTypes.isEmpty()) {
            int len = argumentTypes.size();
            Iterator<ParameterElement> iter = argumentTypes.iterator();
            pushNewArray(builder, Class.class, len);
            for (int i = 0; i < len; i++) {
                ParameterElement type = iter.next();
                pushStoreInArray(
                    builder,
                        i,
                        len,
                        () -> builder.push(JavaModelUtils.getTypeReference(type))
                );

            }
        } else {
            builder.getStatic(TYPE_REFLECTION_UTILS, "EMPTY_CLASS_ARRAY", Type.getType(Class[].class));
        }
        builder.invokeStatic(TYPE_REFLECTION_UTILS, METHOD_GET_REQUIRED_METHOD);
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        throw new IllegalStateException();
    }

    /**
     * @return all added dispatch targets
     */
    public List<DispatchTarget> getDispatchTargets() {
        return dispatchTargets;
    }

    /**
     * @return if intercepted method dispatch have been added
     */
    public boolean isHasInterceptedMethod() {
        return hasInterceptedMethod;
    }

    /**
     * Computes Kotlin default method mask.
     *
     * @param writer          The writer
     * @param argumentsPusher The argument pusher
     * @param parameters      The arguments
     * @return The mask
     */
    public static int computeKotlinDefaultsMask(GeneratorAdapter writer,
                                                BiConsumer<Integer, ParameterElement> argumentsPusher,
                                                List<ParameterElement> parameters) {
        int maskLocal = writer.newLocal(Type.INT_TYPE);
        writer.push(0);
        writer.storeLocal(maskLocal);
        int maskIndex = 1;
        int paramIndex = 0;
        for (ParameterElement parameter : parameters) {
            if (parameter instanceof KotlinParameterElement kp && kp.hasDefault() && !kp.getType().isPrimitive()) {
                Label elseLabel = writer.newLabel();
                argumentsPusher.accept(paramIndex, parameter);
                writer.ifNonNull(elseLabel);
                writer.push(maskIndex);
                writer.loadLocal(maskLocal, Type.INT_TYPE);
                writer.math(GeneratorAdapter.OR, Type.INT_TYPE);
                writer.storeLocal(maskLocal);
                writer.visitLabel(elseLabel);
            }
            maskIndex *= 2;
            paramIndex++;
        }
        return maskLocal;
    }

    /**
     * Dispatch target implementation writer.
     */
    @Internal
    public interface DispatchTarget {

        /**
         * @return true if writer supports dispatch one.
         */
        default boolean supportsDispatchOne() {
            return false;
        }

        /**
         * Generate {@code dispatchOne} with shared state.
         *
         * @param writer The method writer
         * @param methodIndex The method index
         * @param stateMap State map shared for this {@code dispatchOne} method, may be written to
         * @return {@code true} iff the return value is on the top of the stack, {@code false} iff
         * we branched instead
         */
        default boolean writeDispatchOne(GeneratorAdapter writer, int methodIndex, Map<String, DispatchTargetState> stateMap) {
            writeDispatchOne(writer, methodIndex);
            return true;
        }

        /**
         * Generate dispatch one.
         * @param methodIndex The method index
         *
         * @param writer The writer
         */
        default void writeDispatchOne(GeneratorAdapter writer, int methodIndex) {
            throw new IllegalStateException("Not supported");
        }

        /**
         * @return true if writer supports dispatch multi.
         */
        default boolean supportsDispatchMulti() {
            return false;
        }

        /**
         * Generate dispatch multi.
         *
         * @param writer The writer
         * @param methodIndex The method index
         */
        default void writeDispatchMulti(GeneratorAdapter writer, int methodIndex) {
            throw new IllegalStateException("Not supported");
        }

    }

    /**
     * State carried between different {@link DispatchTarget}s. This allows for code size reduction
     * by sharing bytecode in the same method.
     */
    @Internal
    public interface DispatchTargetState {
        /**
         * Complete writing this state.
         *
         * @param writer The method writer
         */
        void complete(GeneratorAdapter writer);
    }

    /**
     * Field get dispatch target.
     */
    @Internal
    public static final class FieldGetDispatchTarget implements DispatchTarget {
        @NonNull
        final FieldElement beanField;

        public FieldGetDispatchTarget(FieldElement beanField) {
            this.beanField = beanField;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return false;
        }

        @Override
        public void writeDispatchOne(GeneratorAdapter writer, int fieldIndex) {
            final Type propertyType = JavaModelUtils.getTypeReference(beanField.getType());
            final Type beanType = JavaModelUtils.getTypeReference(beanField.getOwningType());

            if (beanField.isReflectionRequired()) {
                writer.push(beanType); // Bean class
                writer.push(beanField.getName()); // Field name
                writer.loadArg(1); // Bean instance
                writer.invokeStatic(TYPE_REFLECTION_UTILS, METHOD_GET_FIELD_VALUE);
                if (beanField.isPrimitive()) {
                    pushCastToType(writer, propertyType);
                }
            } else {
                // load this
                writer.loadArg(1);
                pushCastToType(writer, beanType);

                // get field value
                writer.getField(
                    JavaModelUtils.getTypeReference(beanField.getOwningType()),
                    beanField.getName(),
                    propertyType);
            }

            pushBoxPrimitiveIfNecessary(propertyType, writer);
        }

        @NonNull
        public FieldElement getField() {
            return beanField;
        }
    }

    /**
     * Field set dispatch target.
     */
    @Internal
    public static final class FieldSetDispatchTarget implements DispatchTarget {
        @NonNull
        final FieldElement beanField;

        public FieldSetDispatchTarget(FieldElement beanField) {
            this.beanField = beanField;
        }

        @Override
        public boolean supportsDispatchOne() {
            return true;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return false;
        }

        @Override
        public void writeDispatchOne(GeneratorAdapter writer, int fieldIndex) {
            final Type propertyType = JavaModelUtils.getTypeReference(beanField.getType());
            final Type beanType = JavaModelUtils.getTypeReference(beanField.getOwningType());

            if (beanField.isReflectionRequired()) {
                writer.push(beanType); // Bean class
                writer.push(beanField.getName()); // Field name
                writer.loadArg(1); // Bean instance
                writer.loadArg(2); // Field value
                writer.invokeStatic(TYPE_REFLECTION_UTILS, METHOD_SET_FIELD_VALUE);
            } else {
                // load this
                writer.loadArg(1);
                pushCastToType(writer, beanType);

                // load value
                writer.loadArg(2);
                pushCastToType(writer, propertyType);

                // get field value
                writer.putField(
                    beanType,
                    beanField.getName(),
                    propertyType);

            }
            // push null return type
            writer.push((String) null);
        }

        @NonNull
        public FieldElement getField() {
            return beanField;
        }
    }

    /**
     * Method invocation dispatch target.
     */
    @Internal
    @SuppressWarnings("FinalClass")
    public static class MethodDispatchTarget implements DispatchTarget {
        final Type dispatchSuperType;
        final TypedElement declaringType;
        final MethodElement methodElement;
        final boolean oneDispatch;
        final boolean multiDispatch;

        private MethodDispatchTarget(Type dispatchSuperType,
                                     TypedElement declaringType,
                                     MethodElement methodElement,
                                     boolean oneDispatch,
                                     boolean multiDispatch) {
            this.dispatchSuperType = dispatchSuperType;
            this.declaringType = declaringType;
            this.methodElement = methodElement;
            this.oneDispatch = oneDispatch;
            this.multiDispatch = multiDispatch;
        }

        public MethodElement getMethodElement() {
            return methodElement;
        }

        @Override
        public boolean supportsDispatchOne() {
            return oneDispatch;
        }

        @Override
        public boolean supportsDispatchMulti() {
            return multiDispatch;
        }

        @Override
        public void writeDispatchMulti(GeneratorAdapter writer, int methodIndex) {
            writeDispatch(writer, methodIndex, true);
        }

        @Override
        public void writeDispatchOne(GeneratorAdapter writer, int methodIndex) {
            writeDispatch(writer, methodIndex, false);
        }

        private void writeDispatch(GeneratorAdapter writer, int methodIndex, boolean isMulti) {
            String methodName = methodElement.getName();

            List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getSuspendParameters());
            Type declaringTypeObject = JavaModelUtils.getTypeReference(declaringType);
            boolean isKotlinDefault = argumentTypes.stream().anyMatch(p -> p instanceof KotlinParameterElement kp && kp.hasDefault());

            final boolean reflectionRequired = methodElement.isReflectionRequired();
            ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
            boolean isInterface = declaringType.getType().isInterface();
            Type returnTypeObject = JavaModelUtils.getTypeReference(returnType);
            boolean hasArgs = !argumentTypes.isEmpty();

            // load this
            boolean isStaticMethodInvocation = methodElement.isStatic() || isKotlinDefault;

            if (!isStaticMethodInvocation) {
                writer.loadArg(1);
            }

            if (reflectionRequired) {
                if (isStaticMethodInvocation) {
                    writer.push((String) null);
                }
                writer.loadThis();
                writer.push(methodIndex);
                writer.invokeVirtual(dispatchSuperType, GET_ACCESSIBLE_TARGET_METHOD);
                if (hasArgs) {
                    if (isMulti) {
                        writer.loadArg(2);
                    } else {
                        writer.push(1);
                        writer.newArray(Type.getType(Object.class)); // new Object[1]
                        writer.dup(); // one ref to store and one to return
                        writer.push(0);
                        writer.loadArg(2);
                        writer.visitInsn(AASTORE); // objects[0] = argumentAtIndex2
                    }
                } else {
                    writer.getStatic(Type.getType(ArrayUtils.class), "EMPTY_OBJECT_ARRAY", Type.getType(Object[].class));
                }
                writer.invokeStatic(TYPE_REFLECTION_UTILS, METHOD_INVOKE_METHOD);
            } else {
                if (!isStaticMethodInvocation) {
                    pushCastToType(writer, declaringTypeObject);
                }
                int defaultsMaskLocal = -1;
                if (hasArgs) {
                    if (isKotlinDefault) {
                        writer.loadArg(1); // First parameter is the current instance
                        pushCastToType(writer, declaringTypeObject);
                        defaultsMaskLocal = computeKotlinDefaultsMask(writer, (paramIndex, parameterElement) -> {
                            writer.loadArg(2);
                            writer.push(paramIndex);
                            writer.visitInsn(AALOAD);
                        }, argumentTypes);
                    }
                    if (isMulti) {
                        int argCount = argumentTypes.size();
                        Iterator<ParameterElement> argIterator = argumentTypes.iterator();
                        for (int i = 0; i < argCount; i++) {
                            writer.loadArg(2);
                            writer.push(i);
                            writer.visitInsn(AALOAD);
                            // cast the argument value to the correct type
                            pushCastToType(writer, argIterator.next());
                        }
                    } else {
                        writer.loadArg(2);
                        // cast the argument value to the correct type
                        pushCastToType(writer, argumentTypes.iterator().next());
                    }
                }
                Method method = new Method(methodName, getMethodDescriptor(returnType, argumentTypes));
                if (isKotlinDefault) {
                    method = asDefaultKotlinMethod(method, declaringTypeObject);
                    writer.loadLocal(defaultsMaskLocal, Type.INT_TYPE); // Bit mask of defaults
                    writer.push((String) null); // Last parameter is just a marker and is always null
                    writer.invokeStatic(declaringTypeObject, method);
                } else {
                    if (isStaticMethodInvocation) {
                        writer.invokeStatic(declaringTypeObject, method);
                    } else {
                        writer.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                            declaringTypeObject.getInternalName(), method.getName(),
                            method.getDescriptor(), isInterface);
                    }
                }
            }

            if (returnTypeObject.equals(Type.VOID_TYPE)) {
                writer.push((String) null);
            } else if (!reflectionRequired) {
                pushBoxPrimitiveIfNecessary(returnType, writer);
            }
        }

        private Method asDefaultKotlinMethod(Method method, Type declaringTypeObject) {
            Type[] argumentTypes = method.getArgumentTypes();
            int length = argumentTypes.length;
            Type[] newArgumentTypes = new Type[length + 3];
            System.arraycopy(argumentTypes, 0, newArgumentTypes, 1, length);
            newArgumentTypes[0] = declaringTypeObject;
            newArgumentTypes[length + 1] = Type.INT_TYPE;
            newArgumentTypes[length + 2] = Type.getObjectType("java/lang/Object");
            return new Method(method.getName() + "$default", method.getReturnType(), newArgumentTypes);
        }

    }

    /**
     * Interceptable method invocation dispatch target.
     */
    @Internal
    public static final class InterceptableMethodDispatchTarget extends MethodDispatchTarget {
        final String interceptedProxyClassName;
        final String interceptedProxyBridgeMethodName;
        final Type thisType;

        private InterceptableMethodDispatchTarget(Type dispatchSuperType,
                                                  TypedElement declaringType,
                                                  MethodElement methodElement,
                                                  String interceptedProxyClassName,
                                                  String interceptedProxyBridgeMethodName,
                                                  Type thisType) {
            super(dispatchSuperType, declaringType, methodElement, false, true);
            this.interceptedProxyClassName = interceptedProxyClassName;
            this.interceptedProxyBridgeMethodName = interceptedProxyBridgeMethodName;
            this.thisType = thisType;
        }

        @Override
        public void writeDispatchMulti(GeneratorAdapter writer, int methodIndex) {
            List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getSuspendParameters());
            ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
            Type returnTypeObject = JavaModelUtils.getTypeReference(returnType);

            // load this
            writer.loadArg(1);
            // duplicate target
            writer.dup();

            String methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
            Label invokeTargetBlock = new Label();

            Type interceptedProxyType = getObjectType(interceptedProxyClassName);

            // load this.$interceptable field value
            writer.loadThis();
            writer.getField(thisType, FIELD_INTERCEPTABLE, Type.getType(boolean.class));
            // check if it equals true
            writer.push(true);
            writer.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, invokeTargetBlock);

            // target instanceOf intercepted proxy
            writer.loadArg(1);
            writer.instanceOf(interceptedProxyType);
            // check if instanceOf
            writer.push(true);
            writer.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, invokeTargetBlock);

            pushCastToType(writer, interceptedProxyType);

            // load arguments
            Iterator<ParameterElement> iterator = argumentTypes.iterator();
            for (int i = 0; i < argumentTypes.size(); i++) {
                writer.loadArg(2);
                writer.push(i);
                writer.visitInsn(AALOAD);

                pushCastToType(writer, iterator.next());
            }

            writer.visitMethodInsn(INVOKEVIRTUAL,
                    interceptedProxyType.getInternalName(), interceptedProxyBridgeMethodName,
                    methodDescriptor, false);

            if (returnTypeObject.equals(Type.VOID_TYPE)) {
                writer.visitInsn(ACONST_NULL);
            } else {
                pushBoxPrimitiveIfNecessary(returnType, writer);
            }
            writer.returnValue();

            writer.visitLabel(invokeTargetBlock);

            // remove parent
            writer.pop();

            super.writeDispatchMulti(writer, methodIndex);
        }
    }

}
