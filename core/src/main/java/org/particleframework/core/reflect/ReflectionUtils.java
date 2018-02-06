package org.particleframework.core.reflect;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.exception.InvocationException;
import org.particleframework.core.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
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

    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE
            = Collections.unmodifiableMap(new LinkedHashMap<Class<?>, Class<?>>() {
        {
            put(Boolean.class, boolean.class);
            put(Byte.class, byte.class);
            put(Character.class, char.class);
            put(Double.class, double.class);
            put(Float.class, float.class);
            put(Integer.class, int.class);
            put(Long.class, long.class);
            put(Short.class, short.class);
            put(Void.class, void.class);
        }
    });

    private static final Map<Class<?>, Integer> PRIMITIVE_BYTE_SIZES
            = Collections.unmodifiableMap(new LinkedHashMap<Class<?>, Integer>() {
        {
            put(Byte.class, Byte.BYTES);
            put(Character.class, Character.BYTES);
            put(Double.class, Double.BYTES);
            put(Float.class, Float.BYTES);
            put(Integer.class, Integer.BYTES);
            put(Long.class, Long.BYTES);
            put(Short.class, Short.BYTES);
        }
    });

    /**
     * Is the method a setter
     *
     * @param name The method name
     * @param args The arguments
     * @return True if it is
     */
    public static boolean isSetter(String name, Class[] args) {
        if (StringUtils.isEmpty(name) || args == null)return false;
        if (args.length != 1) return false;

        return NameUtils.isSetterName(name);
    }

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
     * Obtain the wrapper type for the given primitive type
     *
     * @param wrapperType The primitive type
     * @return The wrapper type
     */
    public static Class getPrimitiveType(Class wrapperType) {
        Class<?> wrapper = PRIMITIVES_TO_WRAPPERS.get(wrapperType);
        if (wrapper != null) {
            return wrapper;
        } else {
            return wrapperType;
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
     * @param <T> The generic type
     * @return The method
     */
    public static <T> Optional<Constructor<T>> findConstructor(Class<T> type, Class... argTypes) {
        try {
            return Optional.of(type.getDeclaredConstructor(argTypes));
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
     * @param argumentTypes The argument types
     * @return An {@link Optional} contains the method or empty
     */
    public static Method getRequiredMethod(Class type, String name, Class... argumentTypes) {
        try {
            return type.getDeclaredMethod(name, argumentTypes);
        } catch (NoSuchMethodException e) {
            return findMethod(type, name, argumentTypes)
                    .orElseThrow(()-> newNoSuchMethodError(type, name, argumentTypes));
        }
    }

    /**
     * Finds an internal method defined by the Particle API and throws a {@link NoSuchMethodError} if it doesn't exist
     *
     * @param type The type
     * @param name The name
     * @param argumentTypes The argument types
     * @return An {@link Optional} contains the method or empty
     * @throws NoSuchMethodError If the method doesn't exist
     */
    @Internal
    public static Method getRequiredInternalMethod(Class type, String name, Class... argumentTypes) {
        try {
            return type.getDeclaredMethod(name, argumentTypes);
        } catch (NoSuchMethodException e) {
            return findMethod(type, name, argumentTypes)
                    .orElseThrow(()-> newNoSuchMethodInternalError(type, name, argumentTypes));
        }
    }


    /**
     * Finds an internal constructor defined by the Particle API and throws a {@link NoSuchMethodError} if it doesn't exist
     *
     * @param type The type
     * @param argumentTypes The argument types
     * @return An {@link Optional} contains the method or empty
     * @throws NoSuchMethodError If the method doesn't exist
     */
    @Internal
    public static <T> Constructor<T> getRequiredInternalConstructor(Class<T> type, Class... argumentTypes) {
        try {
            return type.getDeclaredConstructor(argumentTypes);
        } catch (NoSuchMethodException e) {
            throw newNoSuchConstructorInternalError(type, argumentTypes);
        }
    }
    /**
     * Finds a field on the given type for the given name
     *
     * @param type The type
     * @param name The name
     * @return An {@link Optional} contains the method or empty
     */
    public static Field getRequiredField(Class type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            Optional<Field> field = findField(type, name);
            return field.orElseThrow(()-> new NoSuchFieldError("No field '"+name+"' found for type: " + type.getName()));
        }
    }

    /**
     * Finds a field in the type or super type
     * @param type The type
     * @param name The field name
     * @return An {@link Optional} of field
     */
    public static Optional<Field> findField(Class type, String name) {
        Optional<Field> declaredField = findDeclaredField(type, name);
        if(!declaredField.isPresent()) {
            type = type.getSuperclass();
            while(type != null) {
                declaredField = findField(type, name);
                if(declaredField.isPresent()) break;
            }
        }
        return declaredField;
    }

    /**
     * Finds a field in the type or super type
     * @param type The type
     * @param name The field name
     * @return An {@link Optional} of field
     */
    public static void setFieldIfPossible(Class type, String name, Object value) {
        Optional<Field> declaredField = findDeclaredField(type, name);
        if(declaredField.isPresent()) {
            Field field = declaredField.get();
            Optional<?> converted = ConversionService.SHARED.convert(value, field.getType());
            if(converted.isPresent()) {
                field.setAccessible(true);
                try {
                    field.set(type, converted.get());
                } catch (IllegalAccessException e) {
                    // ignore
                }
            }
        }
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

    public static Set<Class> getAllInterfaces(Class<?> aClass) {
        Set<Class> interfaces = new HashSet<>();
        return populateInterfaces(aClass, interfaces);
    }

    protected static Set<Class> populateInterfaces(Class<?> aClass, Set<Class> interfaces) {
        Class<?>[] theInterfaces = aClass.getInterfaces();
        interfaces.addAll(Arrays.asList(theInterfaces));
        for (Class<?> theInterface : theInterfaces) {
            populateInterfaces(theInterface, interfaces);
        }
        if(!aClass.isInterface()) {
            Class<?> superclass = aClass.getSuperclass();
            while(superclass != null) {
                populateInterfaces(superclass, interfaces);
                superclass = superclass.getSuperclass();
            }
        }
        return interfaces;
    }

    public static NoSuchMethodError newNoSuchMethodError(Class declaringType, String name, Class[] argumentTypes) {
        Stream<String> stringStream = Arrays.stream(argumentTypes).map(Class::getSimpleName);
        String argsAsText = stringStream.collect(Collectors.joining(","));

        return new NoSuchMethodError("Required method "+name+"("+argsAsText+") not found for class: " + declaringType.getName() + ". This could indicate a classpath issue, an incompatible JVM or out-of-date class metadata. Check your classpath and recompile classes as necessary.");
    }

    private static NoSuchMethodError newNoSuchMethodInternalError(Class declaringType, String name, Class[] argumentTypes) {
        Stream<String> stringStream = Arrays.stream(argumentTypes).map(Class::getSimpleName);
        String argsAsText = stringStream.collect(Collectors.joining(","));

        return new NoSuchMethodError("Particle method "+ declaringType.getName() +"."+name+"("+argsAsText+") not found. Most likely reason for this issue is that you are running a newer version of Particle with code compiled against an older version. Please recompile the offending classes");
    }


    private static NoSuchMethodError newNoSuchConstructorInternalError(Class declaringType, Class[] argumentTypes) {
        Stream<String> stringStream = Arrays.stream(argumentTypes).map(Class::getSimpleName);
        String argsAsText = stringStream.collect(Collectors.joining(","));

        return new NoSuchMethodError("Particle constructor "+ declaringType.getName() +"("+argsAsText+") not found. Most likely reason for this issue is that you are running a newer version of Particle with code compiled against an older version. Please recompile the offending classes");
    }
}
