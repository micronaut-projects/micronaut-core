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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.*;
import io.micronaut.core.beans.AbstractBeanProperty;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writes out {@link io.micronaut.core.beans.BeanProperty} instances to produce {@link io.micronaut.core.beans.BeanIntrospection} information.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
class BeanPropertyWriter extends AbstractClassFileWriter implements Named {

    static final Type TYPE_BEAN_PROPERTY = Type.getType(AbstractBeanProperty.class);
    static final Method METHOD_READ_INTERNAL = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanProperty.class, "readInternal", Object.class));
    static final Method METHOD_WRITE_INTERNAL = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanProperty.class, "writeInternal", Object.class, Object.class));
    static final Method METHOD_GET_BEAN = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(BeanProperty.class, "getDeclaringBean"));
    private static final Method METHOD_WITH_VALUE_INTERNAL = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanProperty.class, "withValueInternal", Object.class, Object.class));
    private static final Method METHOD_INSTANTIATE = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(BeanIntrospection.class, "instantiate", Object[].class));
    protected final Type type;
    protected final Type propertyType;
    protected final Type beanType;
    private final String propertyName;
    private final AnnotationMetadata annotationMetadata;
    private final ClassWriter classWriter;
    private final Map<String, ClassElement> typeArguments;
    private final boolean readOnly;
    private final boolean isMutable;
    private final MethodElement readMethod;
    private final MethodElement writeMethod;
    private final MethodElement withMethod;
    private final HashMap<String, GeneratorAdapter> loadTypeMethods = new HashMap<>();
    private final Map<String, Integer> defaults = new HashMap<>();
    private final TypedElement typeElement;
    private final ClassElement declaringElement;
    private final Type propertyGenericType;
    private final BeanIntrospectionWriter beanIntrospectionWriter;

    /**
     * Default constructor.
     * @param introspectionWriter The outer inspection writer.
     * @param typeElement  The type element
     * @param propertyType The property type
     * @param propertyGenericType The generic type of the property
     * @param propertyName The property name
     * @param readMethod The read method name
     * @param writeMethod The write method name
     * @param withMethod The with method
     * @param isReadOnly Is the property read only
     * @param index The index for the type
     * @param annotationMetadata The annotation metadata
     * @param typeArguments The type arguments for the property
     */
    BeanPropertyWriter(
            @NonNull BeanIntrospectionWriter introspectionWriter,
            @NonNull TypedElement typeElement,
            @NonNull Type propertyType,
            @NonNull Type propertyGenericType,
            @NonNull String propertyName,
            @Nullable MethodElement readMethod,
            @Nullable MethodElement writeMethod,
            @Nullable MethodElement withMethod,
            boolean isReadOnly,
            int index,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments) {
        super(introspectionWriter.getOriginatingElements());
        this.beanIntrospectionWriter = introspectionWriter;
        Type introspectionType = introspectionWriter.getIntrospectionType();
        this.declaringElement = introspectionWriter.getClassElement();
        this.typeElement = typeElement;
        this.beanType = introspectionWriter.getBeanType();
        this.propertyType = propertyType;
        this.propertyGenericType = propertyGenericType;
        this.readMethod = readMethod;
        this.writeMethod = writeMethod;
        this.withMethod = withMethod;
        this.propertyName = propertyName;
        this.readOnly = isReadOnly;
        this.isMutable = !readOnly || hasAssociatedConstructorArgument();
        this.annotationMetadata = annotationMetadata == AnnotationMetadata.EMPTY_METADATA ? null : annotationMetadata;
        this.type = JavaModelUtils.getTypeReference(ClassElement.of(introspectionType.getClassName() + "$$" + index));
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        if (CollectionUtils.isNotEmpty(typeArguments)) {
            this.typeArguments = typeArguments;
        } else {
            this.typeArguments = null;
        }

    }

    /**
     * @return Is this property mutable
     */
    public boolean isMutable() {
        return isMutable;
    }

    @NonNull
    @Override
    public String getName() {
        return type.getClassName();
    }

    /**
     * @return The property name
     */
    @NonNull
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @return The type for this writer.
     */
    public Type getType() {
        return type;
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        try (OutputStream classOutput = classWriterOutputVisitor.visitClass(getName(), getOriginatingElements())) {
            startFinalClass(classWriter, type.getInternalName(), TYPE_BEAN_PROPERTY);

            writeConstructor();

            // the read method
            writeReadMethod();

            // the write method
            writeWriteMethod();

            if (readOnly) {
                // override isReadOnly method
                final GeneratorAdapter isReadOnly = startPublicFinalMethodZeroArgs(classWriter, boolean.class, "isReadOnly");
                isReadOnly.push(true);
                isReadOnly.returnValue();
                isReadOnly.visitMaxs(1, 1);
                isReadOnly.endMethod();
            }

            if (writeMethod != null && readMethod == null) {
                // override isWriteOnly method
                final GeneratorAdapter isWriteOnly = startPublicFinalMethodZeroArgs(classWriter, boolean.class, "isWriteOnly");
                isWriteOnly.push(true);
                isWriteOnly.returnValue();
                isWriteOnly.visitMaxs(1, 1);
                isWriteOnly.endMethod();
            }

            // override isMutable method
            boolean isMutable = this.isMutable;
            String nonMutableMessage = "Cannot mutate property [" + getPropertyName() + "] that is not mutable via a setter method or constructor argument for type: " + beanType.getClassName();
            if (isMutable) {
                if (writeMethod != null) {
                    // in the case where there is a write method we simply generate code to invoke
                    // writeInternal and returned the bean passed as the argument
                    GeneratorAdapter withValueMethod = startPublicMethod(classWriter, METHOD_WITH_VALUE_INTERNAL);
                    // generates
                    // Object withValueInternal(Object bean, Object value) {
                    //     writeInternal(bean, value);
                    //     return bean;
                    // }
                    withValueMethod.loadThis();
                    withValueMethod.loadArgs();
                    withValueMethod.invokeVirtual(type, METHOD_WRITE_INTERNAL);
                    withValueMethod.loadArg(0);
                    withValueMethod.returnValue();
                    withValueMethod.visitMaxs(1, 1);
                    withValueMethod.endMethod();
                } else if (withMethod != null) {
                    // in the case where there is a write method we simply generate code to invoke
                    // writeInternal and returned the bean passed as the argument
                    GeneratorAdapter withValueMethod = startPublicMethod(classWriter, METHOD_WITH_VALUE_INTERNAL);
                    // generates
                    // Object withValueInternal(Object bean, Object value) {
                    //     return ((MyType)bean).withSomething(value);
                    // }
                    withValueMethod.loadArg(0);
                    pushCastToType(withValueMethod, beanType);
                    withValueMethod.loadArg(1);
                    pushCastToType(withValueMethod, propertyType);
                    invokeMethod(withValueMethod, withMethod);
                    withValueMethod.returnValue();
                    withValueMethod.visitMaxs(1, 1);
                    withValueMethod.endMethod();
                } else {
                    // In this case we have to do the copy constructor approach
                    Map<String, BeanPropertyWriter> propertyDefinitions = beanIntrospectionWriter.getPropertyDefinitions();
                    MethodElement constructor = beanIntrospectionWriter.getConstructor();
                    Object[] constructorArguments = null;
                    if (constructor != null) {
                        ParameterElement[] parameters = constructor.getParameters();
                        constructorArguments = new Object[parameters.length];
                        for (int i = 0; i < parameters.length; i++) {
                            ParameterElement parameter = parameters[i];
                            BeanPropertyWriter propertyWriter = propertyDefinitions.get(parameter.getName());
                            if (propertyWriter == this) {
                                constructorArguments[i] = this;
                            } else if (propertyWriter != null) {
                                MethodElement readMethod = propertyWriter.readMethod;
                                if (readMethod != null) {
                                    if (readMethod.getGenericReturnType().isAssignable(parameter.getGenericType())) {
                                        constructorArguments[i] = readMethod;
                                    } else {
                                        isMutable = false;
                                        nonMutableMessage = "Cannot create copy of type [" + beanType.getClassName() + "]. Property of type [" + readMethod.getGenericReturnType().getName() + "] is not assignable to constructor argument [" + parameter.getName() + "]";
                                    }

                                } else {
                                    isMutable = false;
                                    nonMutableMessage = "Cannot create copy of type [" + beanType.getClassName() + "]. Constructor contains argument [" + parameter.getName() + "] that is not a readable property";
                                    break;
                                }
                            } else {
                                isMutable = false;
                                nonMutableMessage = "Cannot create copy of type [" + beanType.getClassName() + "]. Constructor contains argument [" + parameter.getName() + "] that is not a readable property";
                                break;
                            }
                        }
                    }

                    if (isMutable) {
                        GeneratorAdapter withValueMethod = startPublicMethod(classWriter, METHOD_WITH_VALUE_INTERNAL);
                        // generates
                        // Object withValueInternal(Object bean, Object value) {
                        //     return getDeclaringBean().instantiate(bean.getOther(), value);
                        // }
                        withValueMethod.loadThis();
                        withValueMethod.invokeVirtual(type, METHOD_GET_BEAN);
                        Set<MethodElement> readMethods;
                        if (constructorArguments != null) {

                            int len = constructorArguments.length;
                            pushNewArray(withValueMethod, Object.class, len);
                            readMethods = new HashSet<>(len);
                            for (int i = 0; i < len; i++) {
                                Object constructorArgument = constructorArguments[i];
                                pushStoreInArray(withValueMethod, i, len, () -> {
                                    if (constructorArgument instanceof BeanPropertyWriter) {
                                        withValueMethod.loadArg(1);
                                    } else {
                                        MethodElement readMethod = (MethodElement) constructorArgument;
                                        readMethods.add(readMethod);
                                        withValueMethod.loadArg(0);
                                        pushCastToType(withValueMethod, beanType);
                                        ClassElement returnType = invokeReadMethod(withValueMethod, readMethod);
                                        pushBoxPrimitiveIfNecessary(returnType, withValueMethod);
                                    }
                                });
                            }
                        } else {
                            pushNewArray(withValueMethod, Object.class, 0);
                            readMethods = Collections.emptySet();
                        }

                        withValueMethod.invokeInterface(Type.getType(BeanIntrospection.class), METHOD_INSTANTIATE);
                        List<BeanPropertyWriter> readWriteProps = propertyDefinitions.values().stream()
                                .filter(bwp -> bwp.writeMethod != null && bwp.readMethod != null && !readMethods.contains(bwp.readMethod))
                                .collect(Collectors.toList());
                        if (!readWriteProps.isEmpty()) {
                            int beanTypeLocal = withValueMethod.newLocal(beanType);
                            withValueMethod.storeLocal(beanTypeLocal);
                            for (BeanPropertyWriter readWriteProp : readWriteProps) {

                                MethodElement writeMethod = readWriteProp.writeMethod;
                                MethodElement readMethod = readWriteProp.readMethod;
                                withValueMethod.loadLocal(beanTypeLocal);
                                pushCastToType(withValueMethod, beanType);
                                withValueMethod.loadArg(0);
                                pushCastToType(withValueMethod, beanType);
                                invokeReadMethod(withValueMethod, readMethod);
                                ClassElement writeReturnType = writeMethod.getReturnType();
                                String methodDescriptor = getMethodDescriptor(writeReturnType, Arrays.asList(writeMethod.getParameters()));

                                if (declaringElement.isInterface()) {
                                    withValueMethod.invokeInterface(beanType, new Method(writeMethod.getName(), methodDescriptor));
                                } else {
                                    withValueMethod.invokeVirtual(beanType, new Method(writeMethod.getName(), methodDescriptor));
                                }
                                if (!writeReturnType.getName().equals("void")) {
                                    withValueMethod.pop();
                                }
                            }
                            withValueMethod.loadLocal(beanTypeLocal);
                            pushCastToType(withValueMethod, beanType);
                        }
                        withValueMethod.returnValue();
                        withValueMethod.visitMaxs(1, 1);
                        withValueMethod.endMethod();
                    }
                }
            }

            final GeneratorAdapter isWriteOnly = startPublicFinalMethodZeroArgs(classWriter, boolean.class, "hasSetterOrConstructorArgument");
            isWriteOnly.push(isMutable);
            isWriteOnly.returnValue();
            isWriteOnly.visitMaxs(1, 1);
            isWriteOnly.endMethod();

            if (!isMutable) {
                // In this case the bean cannot be mutated via either copy constructor or setter so simply throw an exception
                GeneratorAdapter mutateMethod = startPublicMethod(classWriter, METHOD_WITH_VALUE_INTERNAL);
                mutateMethod.throwException(Type.getType(UnsupportedOperationException.class), nonMutableMessage);
                mutateMethod.visitMaxs(1, 1);
                mutateMethod.endMethod();
            }

            for (GeneratorAdapter generator : loadTypeMethods.values()) {
                generator.visitMaxs(3, 1);
                generator.visitEnd();
            }
            classOutput.write(classWriter.toByteArray());
        }
    }

    @NonNull
    private ClassElement invokeReadMethod(GeneratorAdapter mutateMethod, MethodElement readMethod) {
        ClassElement returnType = readMethod.getReturnType();
        if (declaringElement.isInterface()) {
            mutateMethod.invokeInterface(beanType, new Method(readMethod.getName(), getMethodDescriptor(returnType, Collections.emptyList())));
        } else {
            mutateMethod.invokeVirtual(beanType, new Method(readMethod.getName(), getMethodDescriptor(returnType, Collections.emptyList())));
        }
        return returnType;
    }

    private boolean hasAssociatedConstructorArgument() {
        MethodElement constructor = beanIntrospectionWriter.getConstructor();
        if (constructor != null) {
            ParameterElement[] parameters = constructor.getParameters();
            for (ParameterElement parameter : parameters) {
                if (getPropertyName().equals(parameter.getName())) {
                    return typeElement.getType().isAssignable(parameter.getGenericType());
                }
            }
        }
        return false;
    }

    private void writeWriteMethod() {
        final GeneratorAdapter writeMethod = startPublicMethod(
                classWriter,
                METHOD_WRITE_INTERNAL.getName(),
                void.class.getName(),
                Object.class.getName(),
                Object.class.getName()
        );
        writeMethod.loadArg(0);
        writeMethod.checkCast(beanType);
        writeMethod.loadArg(1);
        pushCastToType(writeMethod, propertyType);
        writeWriteMethod(writeMethod);
        writeMethod.visitInsn(RETURN);
        writeMethod.visitMaxs(1, 1);
        writeMethod.visitEnd();

    }

    /**
     * Generates the write method.
     * @param writeMethod The write method to write
     */
    protected void writeWriteMethod(GeneratorAdapter writeMethod) {
        final boolean hasWriteMethod = this.writeMethod != null;
        final String methodName = hasWriteMethod ? this.writeMethod.getName() : NameUtils.setterNameFor(propertyName);
        final Type returnType = hasWriteMethod ? JavaModelUtils.getTypeReference(this.writeMethod.getReturnType()) : Type.VOID_TYPE;
        if (declaringElement.isInterface()) {
            writeMethod.invokeInterface(
                    beanType,
                    new Method(methodName,
                            getMethodDescriptor(returnType, Collections.singleton(propertyType)))
            );
        } else {
            writeMethod.invokeVirtual(
                    beanType,
                    new Method(methodName,
                            getMethodDescriptor(returnType, Collections.singleton(propertyType)))
            );
        }
    }

    private void writeReadMethod() {
        final GeneratorAdapter readMethod = startPublicMethod(
                classWriter,
                METHOD_READ_INTERNAL.getName(),
                Object.class.getName(),
                Object.class.getName()
        );
        readMethod.loadArg(0);
        pushCastToType(readMethod, beanType);
        writeReadMethod(readMethod);
        pushBoxPrimitiveIfNecessary(propertyType, readMethod);
        readMethod.returnValue();
        readMethod.visitMaxs(1, 1);
        readMethod.visitEnd();
    }

    /**
     * Generates the read method.
     * @param readMethod The read method to generate.
     */
    protected void writeReadMethod(GeneratorAdapter readMethod) {
        final boolean isBoolean = propertyType.getClassName().equals("boolean");
        final String methodName = this.readMethod != null ? this.readMethod.getName() : NameUtils.getterNameFor(propertyName, isBoolean);
        if (declaringElement.isInterface()) {
            readMethod.invokeInterface(beanType, new Method(methodName, getMethodDescriptor(propertyType, Collections.emptyList())));
        } else {
            readMethod.invokeVirtual(beanType, new Method(methodName, getMethodDescriptor(propertyType, Collections.emptyList())));
        }
    }

    private void writeConstructor() {
        // a constructor that takes just the introspection
        final GeneratorAdapter constructor = startConstructor(classWriter, BeanIntrospection.class);

        constructor.loadThis();
        // 1st argument: the introspection
        constructor.loadArg(0);

        // 2nd argument: The property type
        constructor.push(propertyGenericType);

        // 3rd argument: The property name
        constructor.push(propertyName);

        // 4th argument: The annotation metadata
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            final DefaultAnnotationMetadata annotationMetadata = (DefaultAnnotationMetadata) this.annotationMetadata;
            if (annotationMetadata.isEmpty()) {
                constructor.visitInsn(ACONST_NULL);
            } else {
                AnnotationMetadataWriter.instantiateNewMetadata(type, classWriter, constructor, annotationMetadata, defaults, loadTypeMethods);
            }
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        // 5th argument: The type arguments
        if (typeArguments != null) {
            pushTypeArgumentElements(
                    type,
                    classWriter,
                    constructor,
                    typeElement.getName(),
                    typeArguments,
                    new HashMap<>(),
                    loadTypeMethods
            );
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        invokeConstructor(constructor, AbstractBeanProperty.class, BeanIntrospection.class, Class.class, String.class, AnnotationMetadata.class, Argument[].class);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(20, 2);

        constructor.visitEnd();
    }
}
