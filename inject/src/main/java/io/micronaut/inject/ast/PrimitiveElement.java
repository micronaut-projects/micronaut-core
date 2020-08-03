package io.micronaut.inject.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;

public class PrimitiveElement implements ClassElement {

    private static final PrimitiveElement INT = new PrimitiveElement("int", "I");
    private static final PrimitiveElement CHAR = new PrimitiveElement("char", "C");
    private static final PrimitiveElement BOOLEAN = new PrimitiveElement("boolean", "Z");
    private static final PrimitiveElement LONG = new PrimitiveElement("long", "J");
    private static final PrimitiveElement FLOAT = new PrimitiveElement("float", "F");
    private static final PrimitiveElement DOUBLE = new PrimitiveElement("double", "D");
    private static final PrimitiveElement SHORT = new PrimitiveElement("short", "S");
    private static final PrimitiveElement BYTE = new PrimitiveElement("byte", "B");
    public static final PrimitiveElement VOID = new PrimitiveElement("void", "V");

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

    public String getTypeName() {
        return typeName;
    }

    @Override
    public String getName() {
        return className;
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
            if (element.getTypeName().equalsIgnoreCase(name)) {
                return element;
            }
        }
        throw new IllegalArgumentException(String.format("No primitive found for name: %s", name));
    }
}
