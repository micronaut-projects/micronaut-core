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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;

public final class PrimitiveElement implements ArrayableClassElement {

    public static final PrimitiveElement VOID = new PrimitiveElement("void");
    private static final PrimitiveElement INT = new PrimitiveElement("int");
    private static final PrimitiveElement CHAR = new PrimitiveElement("char");
    private static final PrimitiveElement BOOLEAN = new PrimitiveElement("boolean");
    private static final PrimitiveElement LONG = new PrimitiveElement("long");
    private static final PrimitiveElement FLOAT = new PrimitiveElement("float");
    private static final PrimitiveElement DOUBLE = new PrimitiveElement("double");
    private static final PrimitiveElement SHORT = new PrimitiveElement("short");
    private static final PrimitiveElement BYTE = new PrimitiveElement("byte");
    private static final PrimitiveElement[] PRIMITIVES = new PrimitiveElement[] {INT, CHAR, BOOLEAN, LONG, FLOAT, DOUBLE, SHORT, BYTE, VOID};

    private final String typeName;
    private final int arrayDimensions;

    /**
     * Default constructor.
     * @param name The type name
     */
    private PrimitiveElement(String name) {
        this(name, 0);
    }

    /**
     * Default constructor.
     * @param name            The type name
     * @param arrayDimensions The number of array dimensions
     */
    private PrimitiveElement(String name, int arrayDimensions) {
        this.typeName = name;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public boolean isAssignable(String type) {
        return typeName.equals(type);
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
        return new PrimitiveElement(typeName, arrayDimensions);
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
}
