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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A {@link ClassElement} of primitive types.
 */
public final class PrimitiveElement implements ArrayableClassElement {

    public static final PrimitiveElement VOID = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_VOID, null);
    public static final PrimitiveElement BOOLEAN = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_BOOLEAN, Boolean.class);
    public static final PrimitiveElement INT = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_INT, Integer.class);
    public static final PrimitiveElement CHAR = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_CHAR, Character.class);
    public static final PrimitiveElement LONG = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_LONG, Long.class);
    public static final PrimitiveElement FLOAT = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_FLOAT, Float.class);
    public static final PrimitiveElement DOUBLE = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_DOUBLE, Double.class);
    public static final PrimitiveElement SHORT = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_SHORT, Short.class);
    public static final PrimitiveElement BYTE = new PrimitiveElement(ClassUtils.PRIMITIVE_TYPE_NAME_BYTE, Byte.class);
    private static final PrimitiveElement[] PRIMITIVES = new PrimitiveElement[] {INT, CHAR, BOOLEAN, LONG, FLOAT, DOUBLE, SHORT, BYTE, VOID};

    private final String typeName;
    private final int arrayDimensions;
    private final String boxedTypeName;
    private final AnnotationMetadata annotationMetadata;
    @Nullable
    private final MutableAnnotationMetadataDelegate<AnnotationMetadata> typeAnnotationMetadata;
    @Nullable
    private final String doc;

    /**
     * Default constructor.
     * @param name The type name
     */
    private PrimitiveElement(String name, @Nullable Class<?> boxedType) {
        this(name, boxedType == null ? "<>" : boxedType.getName(), 0, AnnotationMetadata.EMPTY_METADATA, null, null);
    }

    /**
     * Default constructor.
     *
     * @param name               The type name
     * @param arrayDimensions    The number of array dimensions
     * @param annotationMetadata The annotation metadata
     * @param doc                The optional documentation
     */
    private PrimitiveElement(String name,
                             String boxedTypeName,
                             int arrayDimensions,
                             AnnotationMetadata annotationMetadata,
                             @Nullable MutableAnnotationMetadataDelegate<AnnotationMetadata> typeAnnotationMetadata,
                             @Nullable String doc) {
        this.typeName = name;
        this.arrayDimensions = arrayDimensions;
        this.boxedTypeName = boxedTypeName;
        this.annotationMetadata = annotationMetadata;
        this.typeAnnotationMetadata = typeAnnotationMetadata;
        this.doc = doc;
    }

    @Override
    public Optional<String> getDocumentation(boolean parse) {
        return Optional.ofNullable(doc);
    }

    @Override
    public boolean isAssignable(String type) {
        return typeName.equals(type) || boxedTypeName.equals(type) || Object.class.getName().equals(type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        if (this == type) {
            return true;
        }
        if (isArray()) {
            if (!type.isPrimitive() || !type.isArray() || type.getArrayDimensions() != getArrayDimensions()) {
                return false;
            }
        }
        return isAssignable(type.getName());
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @Override
    public String getName() {
        return typeName;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Object getNativeType() {
        throw new UnsupportedOperationException("There is no native types for primitives");
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (typeAnnotationMetadata != null) {
            return typeAnnotationMetadata.getAnnotationMetadata();
        }
        return annotationMetadata;
    }

    /**
     * The type annotations written on this use of the primitive, such as {@code @A int}, when the element
     * was created for such a use with {@link #withTypeAnnotationMetadata(MutableAnnotationMetadataDelegate)};
     * they are the same annotations {@link #getAnnotationMetadata()} returns. The shared constants have none.
     *
     * @return The type annotation metadata
     * @since 5.3.0
     */
    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        if (typeAnnotationMetadata != null) {
            return typeAnnotationMetadata;
        }
        return ArrayableClassElement.super.getTypeAnnotationMetadata();
    }

    @Override
    public PrimitiveElement withArrayDimensions(int arrayDimensions) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, annotationMetadata, typeAnnotationMetadata, doc);
    }

    @Override
    public PrimitiveElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, annotationMetadata, null, doc);
    }

    /**
     * A copy of this element carrying the type annotations written on a use of the primitive, such as
     * {@code @A int}. The delegate answers {@link #getAnnotationMetadata()} and
     * {@link #getTypeAnnotationMetadata()}, and receives the annotations a visitor adds to the element, so a
     * use of a primitive can be annotated at compilation time like a use of any other type.
     *
     * @param typeAnnotationMetadata The type annotation metadata of the use
     * @return The annotated copy; it still equals the shared constant
     * @since 5.3.0
     */
    public PrimitiveElement withTypeAnnotationMetadata(MutableAnnotationMetadataDelegate<AnnotationMetadata> typeAnnotationMetadata) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, AnnotationMetadata.EMPTY_METADATA, typeAnnotationMetadata, doc);
    }

    private PrimitiveElement withDoc(String doc) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, annotationMetadata, typeAnnotationMetadata, doc);
    }

    private MutableAnnotationMetadataDelegate<AnnotationMetadata> getAnnotationMetadataToWrite() {
        if (typeAnnotationMetadata == null) {
            throw new UnsupportedOperationException("A primitive without type annotations cannot be annotated at compilation time");
        }
        return typeAnnotationMetadata;
    }

    @Override
    public <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadataToWrite().annotate(annotationType, consumer);
        return this;
    }

    @Override
    public Element annotate(String annotationType) {
        getAnnotationMetadataToWrite().annotate(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        getAnnotationMetadataToWrite().annotate(annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(Class<T> annotationType) {
        getAnnotationMetadataToWrite().annotate(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
        getAnnotationMetadataToWrite().annotate(annotationValue);
        return this;
    }

    @Override
    public Element removeAnnotation(String annotationType) {
        getAnnotationMetadataToWrite().removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotation(Class<T> annotationType) {
        getAnnotationMetadataToWrite().removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        getAnnotationMetadataToWrite().removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public Element removeStereotype(String annotationType) {
        getAnnotationMetadataToWrite().removeStereotype(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeStereotype(Class<T> annotationType) {
        getAnnotationMetadataToWrite().removeStereotype(annotationType);
        return this;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isNonNull() {
        if (this == PrimitiveElement.VOID || isArray()) {
            return ArrayableClassElement.super.isNonNull();
        }
        return true;
    }

    @Override
    public boolean isNullable() {
        if (this == PrimitiveElement.VOID || isArray()) {
            return ArrayableClassElement.super.isNullable();
        }
        return false;
    }

    public static PrimitiveElement valueOf(String name) {
        return valueOf(name, null);
    }

    public static PrimitiveElement valueOf(String name, @Nullable String doc) {
        for (PrimitiveElement element: PRIMITIVES) {
            if (element.getName().equalsIgnoreCase(name)) {
                if (doc != null) {
                    return element.withDoc(doc);
                }
                return element;
            }
        }
        throw new IllegalArgumentException("No primitive found for name: %s".formatted(name));
    }

    @Override
    public String toString() {
        return "PrimitiveElement{" + "typeName='" + typeName + '\'' +
            ", arrayDimensions=" + arrayDimensions +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PrimitiveElement that = (PrimitiveElement) o;
        return arrayDimensions == that.arrayDimensions && typeName.equals(that.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName, arrayDimensions);
    }
}
