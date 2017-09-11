package org.particleframework.core.reflect;

import org.particleframework.core.reflect.exception.InvocationException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

/**
 * Utility methods for reflection related tasks.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ReflectionUtils {
    public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
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
    public static <T> Optional<Constructor<T>> findConstructor(Class<T> type, Class... argTypes) {
        try {
            return Optional.of(type.getConstructor(argTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    /**
     * Invokes a method
     *
     * @param instance The instance
     * @param method The method
     * @param arguments The arguments
     * @param <R> The return type
     * @param <T> The instance type
     * @return The result
     */
    public static <R, T> R invokeMethod(T instance, Method method, Object... arguments) {
        try {
            return (R) method.invoke(instance, arguments);
        } catch (IllegalAccessException e) {
            throw new InvocationException("Illegal access invoking method ["+method+"]: " + e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw new InvocationException("Exception occurred invoking method ["+method+"]: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a method on the given type for the given name
     *
     * @param type The type
     * @param name The name
     * @param argumentTypes The argument types
     * @return An {@link Optional} contains the method or empty
     */
    public static Optional<Method> findMethod(Class type, String name, Class... argumentTypes) {
        Class currentType = type;
        while(currentType != null) {
            Method[] methods = currentType.isInterface() ? currentType.getMethods() : currentType.getDeclaredMethods();
            for (Method method : methods) {
                if(name.equals(method.getName()) && Arrays.equals(argumentTypes, method.getParameterTypes())) {
                    return Optional.of(method);
                }
            }
            currentType = currentType.getSuperclass();
        }
        return Optional.empty();
    }

    /**
     * Finds a method on the given type for the given name
     *
     * @param type The type
     * @param name The name
     * @return An {@link Optional} contains the method or empty
     */
    public static Stream<Method> findMethodsByName(Class type, String name) {
        Class currentType = type;
        Set<Method> methodSet = new HashSet<>();
        while(currentType != null) {
            Method[] methods = currentType.isInterface() ? currentType.getMethods() : currentType.getDeclaredMethods();
            for (Method method : methods) {
                if(name.equals(method.getName())) {
                    methodSet.add(method);
                }
            }
            currentType = currentType.getSuperclass();
        }
        return methodSet.stream();
    }

    public static Optional<Field> findDeclaredField(Class type, String name)  {
        try {
            Field declaredField = type.getDeclaredField(name);
            return Optional.of(declaredField);
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }
}
