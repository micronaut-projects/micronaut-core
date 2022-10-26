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

    /**
     * Default constructor.
     * @param name The type name
     */
    private PrimitiveElement(String name, @Nullable Class<?> boxedType) {
        this(name, boxedType == null ? "<>" : boxedType.getName(), 0);
    }

    /**
     * Default constructor.
     * @param name            The type name
     * @param arrayDimensions The number of array dimensions
     */
    private PrimitiveElement(String name, String boxedTypeName, int arrayDimensions) {
        this.typeName = name;
        this.arrayDimensions = arrayDimensions;
        this.boxedTypeName = boxedTypeName;
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
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new PrimitiveElement(typeName, boxedTypeName, arrayDimensions);
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    public static PrimitiveElement valueOf(String name) {
        for (PrimitiveElement element: PRIMITIVES) {
            if (element.getName().equalsIgnoreCase(name)) {
                return element;
            }
        }
        throw new IllegalArgumentException(String.format("No primitive found for name: %s", name));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PrimitiveElement{");
        sb.append("typeName='").append(typeName).append('\'');
        sb.append(", arrayDimensions=").append(arrayDimensions);
        sb.append('}');
        return sb.toString();
    }
}
