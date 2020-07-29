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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.ast.ClassElement;

/**
 * A primitive type element.
 *
 * @author graemerocher
 * @since 1.0
 */
public class JavaPrimitiveElement implements ClassElement, AnnotationMetadataDelegate {

    private static final JavaPrimitiveElement INT_ARRAY = new JavaPrimitiveElement("[I");
    private static final JavaPrimitiveElement CHAR_ARRAY = new JavaPrimitiveElement("[C");
    private static final JavaPrimitiveElement BOOLEAN_ARRAY = new JavaPrimitiveElement("[Z");
    private static final JavaPrimitiveElement LONG_ARRAY = new JavaPrimitiveElement("[J");
    private static final JavaPrimitiveElement FLOAT_ARRAY = new JavaPrimitiveElement("[F");
    private static final JavaPrimitiveElement DOUBLE_ARRAY = new JavaPrimitiveElement("[D");
    private static final JavaPrimitiveElement SHORT_ARRAY = new JavaPrimitiveElement("[S");
    private static final JavaPrimitiveElement BYTE_ARRAY = new JavaPrimitiveElement("[B");

    private static final JavaPrimitiveElement INT = new JavaPrimitiveElement("int", INT_ARRAY);
    private static final JavaPrimitiveElement CHAR = new JavaPrimitiveElement("char", CHAR_ARRAY);
    private static final JavaPrimitiveElement BOOLEAN = new JavaPrimitiveElement("boolean", BOOLEAN_ARRAY);
    private static final JavaPrimitiveElement LONG = new JavaPrimitiveElement("long", LONG_ARRAY);
    private static final JavaPrimitiveElement FLOAT = new JavaPrimitiveElement("float", FLOAT_ARRAY);
    private static final JavaPrimitiveElement DOUBLE = new JavaPrimitiveElement("double", DOUBLE_ARRAY);
    private static final JavaPrimitiveElement SHORT = new JavaPrimitiveElement("short", SHORT_ARRAY);
    private static final JavaPrimitiveElement BYTE = new JavaPrimitiveElement("byte", BYTE_ARRAY);

    private static final JavaPrimitiveElement[] PRIMITIVES = new JavaPrimitiveElement[] {INT, CHAR, BOOLEAN, LONG, FLOAT, DOUBLE, SHORT, BYTE};


    private final String typeName;
    private final JavaPrimitiveElement arrayType;
    private final boolean isArray;
    /**
     * Default constructor.
     * @param typeName The type name
     */
    private JavaPrimitiveElement(String typeName) {
        this.typeName = typeName;
        this.arrayType = null;
        this.isArray = true;
    }

    /**
     * Default constructor.
     * @param typeName The type name
     * @param arrayType The corresponding array type
     */
    private JavaPrimitiveElement(String typeName, JavaPrimitiveElement arrayType) {
        this.typeName = typeName;
        this.arrayType = arrayType;
        this.isArray = false;
    }

    @Override
    public boolean isAssignable(String type) {
        return false;
    }

    @Override
    public boolean isArray() {
        return isArray;
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
        if (isArray()) {
            return ClassUtils.forName(typeName, null).orElse(null);
        } else {
            return ClassUtils.getPrimitiveType(typeName).orElse(null);
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Converts the array representation of this primitive type.
     * @return The array rep
     */
    JavaPrimitiveElement toArray() {
        if (isArray()) {
            return new JavaPrimitiveElement("[" + typeName);
        } else {
            return arrayType;
        }
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    public static JavaPrimitiveElement valueOf(String name) {
        for (JavaPrimitiveElement element: PRIMITIVES) {
            if (element.getName().equalsIgnoreCase(name)) {
                return element;
            }
        }
        throw new IllegalArgumentException(String.format("No primitive found for name: %s", name));
    }
}
