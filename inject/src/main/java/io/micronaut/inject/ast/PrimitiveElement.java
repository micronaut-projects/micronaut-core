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

public final class PrimitiveElement implements ClassElement {

    public static final PrimitiveElement VOID = new PrimitiveElement("void", "V");

    private static final PrimitiveElement INT = new PrimitiveElement("int", "I");
    private static final PrimitiveElement CHAR = new PrimitiveElement("char", "C");
    private static final PrimitiveElement BOOLEAN = new PrimitiveElement("boolean", "Z");
    private static final PrimitiveElement LONG = new PrimitiveElement("long", "J");
    private static final PrimitiveElement FLOAT = new PrimitiveElement("float", "F");
    private static final PrimitiveElement DOUBLE = new PrimitiveElement("double", "D");
    private static final PrimitiveElement SHORT = new PrimitiveElement("short", "S");
    private static final PrimitiveElement BYTE = new PrimitiveElement("byte", "B");
    private static final PrimitiveElement[] PRIMITIVES = new PrimitiveElement[] {INT, CHAR, BOOLEAN, LONG, FLOAT, DOUBLE, SHORT, BYTE, VOID};

    private final String typeName;
    private final String className;
    private final int arrayDimensions;

    /**
     * Default constructor.
     * @param name The type name
     * @param className The class name
     */
    private PrimitiveElement(String name, String className) {
        this.typeName = name;
        this.className = className;
        this.arrayDimensions = 0;
    }

    /**
     * Default constructor.
     * @param name The type name
     * @param className The class name
     * @param arrayDimensions The array dimension count
     */
    private PrimitiveElement(String name, String className, int arrayDimensions) {
        this.typeName = name;
        this.className = className;
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

    public String getInternalName() {
        return className;
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

    @NonNull
    @Override
    public Object getNativeType() {
        throw new UnsupportedOperationException("There is no native types for primitives");
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Converts the array representation of this primitive type.
     * @return The array rep
     */
    public ClassElement toArray() {
        return new PrimitiveElement(typeName, className, arrayDimensions + 1);
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
