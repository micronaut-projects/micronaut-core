package org.particleframework.core.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Utility methods for dealing with generic types
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GenericTypeUtils {

    /**
     * Resolves a single generic type argument from the super class of the given type
     *
     * @param type The type to resolve from
     * @return A single Class or null
     */
    public static Optional<Class> resolveSuperGenericTypeArgument(Class type) {
        try {
            Type genericSuperclass = type.getGenericSuperclass();
            if(genericSuperclass instanceof ParameterizedType) {
                return resolveSingleTypeArgument(genericSuperclass);
            }
            return Optional.empty();
        } catch (NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a single type argument from the given interface of the given class
     *
     * @param type The type to resolve from
     * @param interfaceType The interface to resolve for
     * @return The class or null
     */
    public static Optional<Class> resolveInterfaceTypeArgument(Class type, Class interfaceType) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if(genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if( pt.getRawType() == interfaceType ) {
                    return resolveSingleTypeArgument(genericInterface);
                }
            }
        }
        Class superClass = type.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return resolveInterfaceTypeArgument(superClass, interfaceType);
        }
        return Optional.empty();
    }

    /**
     * Resolve a single type from the given generic type
     *
     * @param genericType The generic type
     * @return An {@link Optional} of the type
     */
    public static Optional<Class> resolveSingleTypeArgument(Type genericType) {
        if(genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if(actualTypeArguments.length == 1) {
                Type actualTypeArgument = actualTypeArguments[0];
                return resolveParameterizedTypeArgument(actualTypeArgument);
            }
        }
        return Optional.empty();
    }

    protected static Optional<Class> resolveParameterizedTypeArgument(Type actualTypeArgument) {
        ParameterizedType pt;
        if(actualTypeArgument instanceof Class)  {
            return Optional.of((Class) actualTypeArgument);
        }
        else if(actualTypeArgument instanceof ParameterizedType) {
            pt = (ParameterizedType) actualTypeArgument;
            Type rawType = pt.getRawType();
            if(rawType instanceof Class) {
                return Optional.of((Class)rawType);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the type arguments for a generic type
     *
     * @param genericType The generic type
     * @return The type arguments
     */
    public static Class[] resolveTypeArguments(Type genericType) {
        Class[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        if(genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            typeArguments = resolveParameterizedType(pt);
        }
        return typeArguments;
    }




    /**
     * Resolves a single generic type argument for the given field
     *
     * @param field The field
     * @return The type argument or {@link Optional#empty()}
     */
    public static Optional<Class> resolveGenericTypeArgument(Field field) {
        Type genericType = field != null ? field.getGenericType() : null;
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length>0) {
                Type typeArg = typeArguments[0];
                return resolveParameterizedTypeArgument(typeArg);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves all of the type arguments for the given field
     *
     * @param field The field
     * @return The type arguments as a class array
     */
    public static Class[] resolveGenericTypeArguments(Field field) {
        Class[] genericClasses = ReflectionUtils.EMPTY_CLASS_ARRAY;
        Type genericType = field != null ? field.getGenericType() : null;
        if (genericType instanceof ParameterizedType) {
            return resolveParameterizedType(((ParameterizedType) genericType));
        }
        return genericClasses;
    }


    /**
     * Resolve all of the type arguments for the given interface from the given type
     *
     * @param type The type to resolve from
     * @param interfaceType The interface to resolve from
     * @return The type arguments to the interface
     */
    public static Class[] resolveInterfaceTypeArguments(Class<?> type, Class<?> interfaceType) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if(genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if( pt.getRawType() == interfaceType ) {
                    return resolveTypeArguments(genericInterface);
                }
            }
        }
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }


    /**
     * Resolve all of the type arguments for the given super type from the given type
     *
     * @param type The type to resolve from
     * @param superType The interface to resolve from
     * @return The type arguments to the interface
     */
    public static Class[] resolveSuperTypeGenericArguments(Class<?> type, Class<?> superType) {
        Type superclass = type.getGenericSuperclass();
        while(superclass != null) {
            if(superclass instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) superclass;
                if( pt.getRawType() == superType ) {
                    return resolveTypeArguments(superclass);
                }
            }

            superclass = superType.getGenericSuperclass();
        }
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    private static Class[] resolveParameterizedType(ParameterizedType pt) {
        Class[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        Type[] actualTypeArguments = pt.getActualTypeArguments();
        if(actualTypeArguments != null && actualTypeArguments.length > 0) {
            typeArguments = new Class[actualTypeArguments.length];
            for (int i = 0; i < actualTypeArguments.length; i++) {
                Type actualTypeArgument = actualTypeArguments[i];
                Optional<Class> opt = resolveParameterizedTypeArgument(actualTypeArgument);
                if(opt.isPresent()) {
                    typeArguments[i] = opt.get();
                }
                else {
                    typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
                    break;
                }
            }
        }
        return typeArguments;
    }
}
