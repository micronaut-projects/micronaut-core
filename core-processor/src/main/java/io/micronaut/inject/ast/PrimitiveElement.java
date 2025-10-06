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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link ClassElement} of primitive types.
 */
public final class PrimitiveElement implements ArrayableClassElement {

    public static final PrimitiveElement VOID = new PrimitiveElement("void", null);
    public static final PrimitiveElement BOOLEAN = new PrimitiveElement("boolean", Boolean.class);
    public static final PrimitiveElement INT = new PrimitiveElement("int", Integer.class);
    public static final PrimitiveElement CHAR = new PrimitiveElement("char", Character.class);
    public static final PrimitiveElement LONG = new PrimitiveElement("long", Long.class);
    public static final PrimitiveElement FLOAT = new PrimitiveElement("float", Float.class);
    public static final PrimitiveElement DOUBLE = new PrimitiveElement("double", Double.class);
    public static final PrimitiveElement SHORT = new PrimitiveElement("short", Short.class);
    public static final PrimitiveElement BYTE = new PrimitiveElement("byte", Byte.class);
    private static final PrimitiveElement[] PRIMITIVES = new PrimitiveElement[] {INT, CHAR, BOOLEAN, LONG, FLOAT, DOUBLE, SHORT, BYTE, VOID};

    private final String typeName;
    private final int arrayDimensions;
    private final String boxedTypeName;
    private final AnnotationMetadata annotationMetadata;
    @Nullable
    private final String doc;

    /**
     * Default constructor.
     * @param name The type name
     */
    private PrimitiveElement(String name, @Nullable Class<?> boxedType) {
        this(name, boxedType == null ? "<>" : boxedType.getName(), 0, AnnotationMetadata.EMPTY_METADATA, null);
    }

    /**
     * Default constructor.
     *
     * @param name               The type name
     * @param arrayDimensions    The number of array dimensions
     * @param annotationMetadata The annotation metadata
     * @param doc                The optional documentation
     */
    private PrimitiveElement(String name, String boxedTypeName, int arrayDimensions, AnnotationMetadata annotationMetadata, String doc) {
        this.typeName = name;
        this.arrayDimensions = arrayDimensions;
        this.boxedTypeName = boxedTypeName;
        this.annotationMetadata = annotationMetadata;
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
    @NonNull
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

    @NonNull
    @Override
    public Object getNativeType() {
        throw new UnsupportedOperationException("There is no native types for primitives");
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public PrimitiveElement withArrayDimensions(int arrayDimensions) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, annotationMetadata, doc);
    }

    @Override
    public PrimitiveElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, annotationMetadata, doc);
    }

    private PrimitiveElement withDoc(String doc) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions, annotationMetadata, doc);
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

    public static PrimitiveElement valueOf(String name, String doc) {
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
