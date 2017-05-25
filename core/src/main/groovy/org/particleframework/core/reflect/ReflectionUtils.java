package org.particleframework.core.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class ReflectionUtils {

    /**
     * Obtains a declared method
     * @param type The type
     * @param methodName The method name
     * @param argTypes The argument types
     * @return The method
     */
    public static Optional<Method> getDeclaredMethod(Class type, String methodName, Class...argTypes) {
        try {
            return Optional.of( type.getDeclaredMethod(methodName, argTypes) );
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    /**
     * Obtains a declared method
     * @param type The type
     * @param argTypes The argument types
     * @return The method
     */
    public static Optional<Constructor> getConstructor(Class type, Class...argTypes) {
        try {
            return Optional.of( type.getConstructor(argTypes) );
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
