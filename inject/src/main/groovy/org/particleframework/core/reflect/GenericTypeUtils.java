package org.particleframework.core.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

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
            ParameterizedType pt = (ParameterizedType) genericSuperclass;
            return (Class) pt.getActualTypeArguments()[0];
        }
        return null;
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
}
