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
import io.micronaut.inject.beans.AbstractBeanProperty;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import org.jetbrains.annotations.NotNull;
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
import java.util.LinkedHashMap;
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
    private final Map<String, Object> typeArguments;
    private final Type beanType;

    /**
     * Default constructor.
     * @param introspectionWriter The outer inspection writer.
     * @param propertyType The property type
     * @param propertyName The property name
     * @param index The index for the type
     * @param annotationMetadata The annotation metadata
     * @param typeArguments The type arguments for the property
     */
    BeanPropertyWriter(
            @Nonnull BeanIntrospectionWriter introspectionWriter,
            @Nonnull Type propertyType,
            @Nonnull String propertyName,
            int index,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments) {

        Type introspectionType = introspectionWriter.getIntrospectionType();
        this.beanType = introspectionWriter.getBeanType();
        this.propertyType = propertyType;
        this.propertyName = propertyName;
        this.annotationMetadata = annotationMetadata == AnnotationMetadata.EMPTY_METADATA ? null : annotationMetadata;
        this.type = getTypeReference(introspectionType.getClassName() + "$$" + index);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        if (CollectionUtils.isNotEmpty(typeArguments)) {
            this.typeArguments = toTypeArguments(typeArguments);
        } else {
            this.typeArguments = null;
        }

    }

    @NotNull
    private Map<String, Object> toTypeArguments(@NotNull Map<String, ClassElement> typeArguments) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>(typeArguments.size());
        for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
            final ClassElement ce = entry.getValue();
            final Map<String, ClassElement> subArgs = ce.getTypeArguments();
            if (CollectionUtils.isNotEmpty(subArgs)) {
                map.put(entry.getKey(), toTypeArguments(subArgs));
            } else {
                final Type typeReference = getTypeForElement(ce);
                map.put(entry.getKey(), typeReference);
            }
        }

        return map;
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
        writeMethod.invokeVirtual(
                beanType,
                new Method(NameUtils.setterNameFor(propertyName),
                        getMethodDescriptor(void.class, Collections.singleton(propertyType)))
        );
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
        readMethod.invokeVirtual(beanType, new Method(NameUtils.getterNameFor(propertyName, isBoolean), getMethodDescriptor(propertyType, Collections.emptyList())));
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
                AnnotationMetadataWriter.instantiateNewMetadata(type, classWriter, constructor, annotationMetadata, new HashMap<>());
            }
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        // 5th argument: The type arguments
        if (typeArguments != null) {
            pushTypeArguments(constructor, typeArguments);
        } else {
            constructor.visitInsn(ACONST_NULL);
        }

        invokeConstructor(constructor, AbstractBeanProperty.class, BeanIntrospection.class, Class.class, String.class, AnnotationMetadata.class, Argument[].class);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 2);
        constructor.visitEnd();
    }
}
