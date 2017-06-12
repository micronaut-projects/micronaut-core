package org.particleframework.core.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class ReflectionUtils {
    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS
            = Collections.unmodifiableMap(new LinkedHashMap<Class<?>, Class<?>>() {
        {
            put(boolean.class, Boolean.class);
            put(byte.class, Byte.class);
            put(char.class, Character.class);
            put(double.class, Double.class);
            put(float.class, Float.class);
            put(int.class, Integer.class);
            put(long.class, Long.class);
            put(short.class, Short.class);
            put(void.class, Void.class);
        }
    });

    /**
     * Obtain the wrapper type for the given primitive type
     *
     * @param primitiveType The primitive type
     * @return The wrapper type
     */
    public static Class getWrapperType(Class primitiveType) {
        if (primitiveType.isPrimitive()) {
            return PRIMITIVES_TO_WRAPPERS.get(primitiveType);
        } else {
            return primitiveType;
        }
    }

    /**
     * Obtains a declared method
     *
     * @param type       The type
     * @param methodName The method name
     * @param argTypes   The argument types
     * @return The method
     */
    public static Optional<Method> getDeclaredMethod(Class type, String methodName, Class... argTypes) {
        try {
            return Optional.of(type.getDeclaredMethod(methodName, argTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    /**
     * Obtains a declared method
     *
     * @param type     The type
     * @param argTypes The argument types
     * @return The method
     */
    public static Optional<Constructor> getConstructor(Class type, Class... argTypes) {
        try {
            return Optional.of(type.getConstructor(argTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
