/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.core.beans.AbstractBeanProperty;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes out {@link io.micronaut.core.beans.BeanProperty} instances to produce {@link io.micronaut.core.beans.BeanIntrospection} information.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
class BeanPropertyWriter extends AbstractClassFileWriter implements Named {

    private static final Type TYPE_BEAN_PROPERTY = getTypeReference(AbstractBeanProperty.class);
    private static final Method METHOD_READ_INTERNAL = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanProperty.class, "readInternal", Object.class));
    private static final Method METHOD_WRITE_INTERNAL = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(AbstractBeanProperty.class, "writeInternal", Object.class, Object.class));
    private final Type propertyType;
    private final String propertyName;
    private final AnnotationMetadata annotationMetadata;
    private final Type type;
    private final ClassWriter classWriter;
    private final Map<String, ClassElement> typeArguments;
    private final Type beanType;
    private final boolean readOnly;
    private final MethodElement readMethod;
    private final MethodElement writeMethod;
    private final HashMap<String, GeneratorAdapter> loadTypeMethods = new HashMap<>();
    private final TypedElement typeElement;

    /**
     * Default constructor.
     * @param introspectionWriter The outer inspection writer.
     * @param typeElement  The type element
     * @param propertyType The property type
     * @param propertyName The property name
     * @param readMethod The read method name
     * @param writeMethod The write method name
     * @param isReadOnly Is the property read only
     * @param index The index for the type
     * @param annotationMetadata The annotation metadata
     * @param typeArguments The type arguments for the property
     */
    BeanPropertyWriter(
            @Nonnull BeanIntrospectionWriter introspectionWriter,
            @Nonnull TypedElement typeElement,
            @Nonnull Type propertyType,
            @Nonnull String propertyName,
            @Nullable MethodElement readMethod,
            @Nullable MethodElement writeMethod,
            boolean isReadOnly,
            int index,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments) {

        Type introspectionType = introspectionWriter.getIntrospectionType();
        this.typeElement = typeElement;
        this.beanType = introspectionWriter.getBeanType();
        this.propertyType = propertyType;
        this.readMethod = readMethod;
        this.writeMethod = writeMethod;
        this.propertyName = propertyName;
        this.readOnly = isReadOnly;
        this.annotationMetadata = annotationMetadata == AnnotationMetadata.EMPTY_METADATA ? null : annotationMetadata;
        this.type = getTypeReference(introspectionType.getClassName() + "$$" + index);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        if (CollectionUtils.isNotEmpty(typeArguments)) {
            this.typeArguments = typeArguments;
        } else {
            this.typeArguments = null;
        }

    }

    @Nonnull
    @Override
    public String getName() {
        return type.getClassName();
    }

    /**
     * @return The property name
     */
    @Nonnull
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
        try (OutputStream classOutput = classWriterOutputVisitor.visitClass(getName())) {
            startFinalClass(classWriter, type.getInternalName(), TYPE_BEAN_PROPERTY);

            writeConstructor();

            // the read method
            writeReadMethod();

            // the write method
            writeWriteMethod();

            if (annotationMetadata != null && annotationMetadata instanceof DefaultAnnotationMetadata) {
                final DefaultAnnotationMetadata annotationMetadata = (DefaultAnnotationMetadata) this.annotationMetadata;
                if (!annotationMetadata.isEmpty()) {
                    AnnotationMetadataWriter.writeAnnotationDefaults(annotationMetadata, classWriter, type, loadTypeMethods);
                }
            }

            if (readOnly) {
                // override isReadOnly method
                final GeneratorAdapter isReadOnly = startPublicMethodZeroArgs(classWriter, boolean.class, "isReadOnly");
                isReadOnly.push(true);
                isReadOnly.returnValue();
                isReadOnly.visitMaxs(1, 1);
                isReadOnly.endMethod();
            }

            if (writeMethod != null && readMethod == null) {
                // override isReadOnly method
                final GeneratorAdapter isReadOnly = startPublicMethodZeroArgs(classWriter, boolean.class, "isWriteOnly");
                isReadOnly.push(true);
                isReadOnly.returnValue();
                isReadOnly.visitMaxs(1, 1);
                isReadOnly.endMethod();
            }

            for (GeneratorAdapter generator : loadTypeMethods.values()) {
                generator.visitMaxs(3, 1);
                generator.visitEnd();
            }
            classOutput.write(classWriter.toByteArray());
        }
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
        final boolean hasWriteMethod = this.writeMethod != null;
        final String methodName = hasWriteMethod ? this.writeMethod.getName() : NameUtils.setterNameFor(propertyName);
        final Object returnType = hasWriteMethod ? getTypeForElement(this.writeMethod.getReturnType()) : void.class;
        if (hasWriteMethod && this.writeMethod.getDeclaringType().isInterface()) {
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
        writeMethod.visitInsn(RETURN);
        writeMethod.visitMaxs(1, 1);
        writeMethod.visitEnd();

    }

    private void writeReadMethod() {
        final GeneratorAdapter readMethod = startPublicMethod(
                classWriter,
                METHOD_READ_INTERNAL.getName(),
                Object.class.getName(),
                Object.class.getName()
        );
        readMethod.loadArg(0);
        pushCastToType(readMethod, beanType.getClassName());
        final boolean isBoolean = propertyType.getClassName().equals("boolean");
        final String methodName = this.readMethod != null ? this.readMethod.getName() : NameUtils.getterNameFor(propertyName, isBoolean);
        if (this.readMethod != null && this.readMethod.getDeclaringType().isInterface()) {
            readMethod.invokeInterface(beanType, new Method(methodName, getMethodDescriptor(propertyType, Collections.emptyList())));
        } else {
            readMethod.invokeVirtual(beanType, new Method(methodName, getMethodDescriptor(propertyType, Collections.emptyList())));
        }
        pushBoxPrimitiveIfNecessary(propertyType, readMethod);
        readMethod.returnValue();
        readMethod.visitMaxs(1, 1);
        readMethod.visitEnd();
    }

    private void writeConstructor() {
        // a constructor that takes just the introspection
        final GeneratorAdapter constructor = startConstructor(classWriter, BeanIntrospection.class);

        constructor.loadThis();
        // 1st argument: the introspection
        constructor.loadArg(0);

        // 2nd argument: The property type
        constructor.push(propertyType);

        // 3rd argument: The property name
        constructor.push(propertyName);

        // 4th argument: The annotation metadata
        if (annotationMetadata != null && annotationMetadata instanceof DefaultAnnotationMetadata) {
            final DefaultAnnotationMetadata annotationMetadata = (DefaultAnnotationMetadata) this.annotationMetadata;
            if (annotationMetadata.isEmpty()) {
                constructor.visitInsn(ACONST_NULL);
            } else {
                AnnotationMetadataWriter.instantiateNewMetadata(type, classWriter, constructor, annotationMetadata, loadTypeMethods);
            }
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        // 5th argument: The type arguments
        if (typeArguments != null) {
            pushTypeArgumentElements(constructor, typeElement, typeArguments);
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        invokeConstructor(constructor, AbstractBeanProperty.class, BeanIntrospection.class, Class.class, String.class, AnnotationMetadata.class, Argument[].class);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(20, 2);

        constructor.visitEnd();
    }
}
