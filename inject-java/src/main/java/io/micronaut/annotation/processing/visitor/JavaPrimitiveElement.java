/*
 * Copyright 2017-2020 original authors
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
public enum JavaPrimitiveElement implements ClassElement, AnnotationMetadataDelegate {

    INT("int"),
    CHAR("char"),
    BOOLEAN("boolean"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    SHORT("short"),
    BYTE("byte"),
    INT_ARRAY("int", true),
    CHAR_ARRAY("char", true),
    BOOLEAN_ARRAY("boolean", true),
    LONG_ARRAY("long", true),
    FLOAT_ARRAY("float", true),
    DOUBLE_ARRAY("double", true),
    SHORT_ARRAY("short", true),
    BYTE_ARRAY("byte", true);

    private final String typeName;
    private final boolean isArray;
    /**
     * Default constructor.
     * @param typeName The type name
     */
    JavaPrimitiveElement(String typeName) {
        this.typeName = typeName;
        this.isArray = false;
    }

    /**
     * Default constructor.
     * @param typeName The type name
     * @param array True it is an array
     */
    JavaPrimitiveElement(String typeName, boolean array) {
        this.typeName = typeName;
        this.isArray = array;
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
        return ClassUtils.getPrimitiveType(typeName).orElse(null);
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
            throw new IllegalStateException("Already an array");
        } else {
            return valueOf(name() + "_ARRAY");
        }
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }
}
