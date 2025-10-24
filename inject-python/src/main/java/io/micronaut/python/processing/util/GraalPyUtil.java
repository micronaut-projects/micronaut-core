package io.micronaut.python.processing.util;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import org.graalvm.polyglot.Value;

public final class GraalPyUtil {
    /**
     * Utility method to convert GraalPy Value objects to Java types.
     * This extracts the common type conversion logic used for both annotations and attribute values.
     *
     * @param value the GraalPy Value to convert
     * @return the converted Java object, or the original value if conversion is not possible
     */
    public static Object convertValueToJava(Value value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isNumber()) {
            if (value.fitsInByte()) {
                return value.asByte();
            } else if (value.fitsInShort()) {
                return value.asShort();
            } else if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else if (value.fitsInFloat()) {
                return value.asFloat();
            } else if (value.fitsInDouble()) {
                return value.asDouble();
            } else {
                return value.asString();
            }
        } else if (value.isString()) {
            return value.asString();
        }
        return value;
    }

    /**
     * Resolves a Python type annotation to a Java ClassElement.
     * Attempts to map Python primitive types to equivalent Java primitive types using PrimitiveElement,
     * otherwise falls back to visitor context lookup.
     *
     * @param typeAnnotation the Python type annotation string (e.g., "int", "str", "bool", "float")
     * @param visitorContext the visitor context for class element lookup
     * @return the resolved ClassElement, or Object ClassElement if resolution fails
     */
    public static ClassElement resolvePythonTypeToJava(String typeAnnotation, PythonVisitorContext visitorContext) {
        if (typeAnnotation == null || typeAnnotation.isBlank()) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }

        // Try to map Python primitive types to Java primitives
        switch (typeAnnotation) {
            case "int":
                return PrimitiveElement.INT;
            case "float":
                return PrimitiveElement.DOUBLE;
            case "bool":
                return PrimitiveElement.BOOLEAN;
            case "str":
                return visitorContext.getClassElement(String.class).orElse(ClassElement.of(String.class));
            default:
                // Fall back to visitor context lookup
                return visitorContext.getClassElement(typeAnnotation).orElse(
                    visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class))
                );
        }
    }
}
