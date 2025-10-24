package io.micronaut.python.processing.util;

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
}
