package org.particleframework.core.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utility methods for dealing with generic types
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GenericTypeUtils {

    public static Class resolveSuperGenericTypeArgument(Class type) {
        Type genericSuperclass = type.getGenericSuperclass();
        if(genericSuperclass instanceof ParameterizedType) {
            return resolveSingleTypeArgument(genericSuperclass);
        }
        return null;
    }

    public static Class resolveInterfaceTypeArgument(Class type, Class interfaceType) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if(genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if( pt.getRawType() == interfaceType ) {
                    Class actualTypeArguments = resolveSingleTypeArgument(genericInterface);
                    if (actualTypeArguments != null) return actualTypeArguments;
                }
            }
        }
        return null;
    }

    public static Class resolveSingleTypeArgument(Type genericType) {
        if(genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if(actualTypeArguments.length == 1) {
                return (Class) actualTypeArguments[0];
            }
        }
        return null;
    }

    /**
     * Resolves the type arguments for a generic type
     *
     * @param genericType The generic type
     * @return The type arguments
     */
    public static Class[] resolveTypeArguments(Type genericType) {
        Class[] typeArguments = null;
        if(genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if(actualTypeArguments != null) {
                typeArguments = new Class[actualTypeArguments.length];
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    Type actualTypeArgument = actualTypeArguments[i];
                    if(actualTypeArgument instanceof Class) {
                        typeArguments[i] = (Class)actualTypeArgument;
                    }
                }
            }
        }
        return typeArguments;
    }


    public static Class resolveGenericTypeArgument(Field field) {
        Type genericType = field != null ? field.getGenericType() : null;
        Class genericClass = null;
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length>0) {
                Type typeArg = typeArguments[0];
                if(typeArg instanceof Class) {
                    genericClass = (Class) typeArg;
                }
            }
        }
        return genericClass;
    }

    public static Class[] resolveGenericTypeArguments(Field field) {
        Class[] genericClasses = new Class[0];
        Type genericType = field != null ? field.getGenericType() : null;
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length>0) {
                genericClasses = new Class[typeArguments.length];
                for (int i = 0; i < typeArguments.length; i++) {
                    Type typeArgument = typeArguments[i];
                    if(typeArgument instanceof Class) {
                        genericClasses[i] = (Class) typeArgument;
                    }
                    else {
                        return new Class[0];
                    }
                }
            }
        }
        return genericClasses;
    }
}
