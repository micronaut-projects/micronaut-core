/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.reflect;

import io.micronaut.core.util.ArrayUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility methods for dealing with generic types via reflection. Generally reflection is to be avoided in Micronaut. Hence
 * this class is regarded as internal and used for only certain niche cases.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GenericTypeUtils {


    /**
     * Resolves a single generic type argument for the given field.
     *
     * @param field The field
     * @return The type argument or {@link Optional#empty()}
     */
    public static Optional<Class> resolveGenericTypeArgument(Field field) {
        Type genericType = field != null ? field.getGenericType() : null;
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type typeArg = typeArguments[0];
                return resolveParameterizedTypeArgument(typeArg);
            }
        }
        return Optional.empty();
    }


    /**
     * Resolve all of the type arguments for the given interface from the given type. Also
     * searches superclasses.
     *
     * @param type          The type to resolve from
     * @param interfaceType The interface to resolve from
     * @return The type arguments to the interface
     */
    public static Class[] resolveInterfaceTypeArguments(Class<?> type, Class<?> interfaceType) {
        Optional<Type> resolvedType = getAllGenericInterfaces(type)
                .stream()
                .filter(t -> {
                            if (t instanceof ParameterizedType) {
                                ParameterizedType pt = (ParameterizedType) t;
                                return pt.getRawType() == interfaceType;
                            }
                            return false;
                        }
                )
                .findFirst();
        return resolvedType.map(GenericTypeUtils::resolveTypeArguments)
                .orElse(ReflectionUtils.EMPTY_CLASS_ARRAY);
    }


    /**
     * Resolve all of the type arguments for the given super type from the given type.
     *
     * @param type      The type to resolve from
     * @param superTypeToResolve The suepr type to resolve from
     * @return The type arguments to the interface
     */
    public static Class[] resolveSuperTypeGenericArguments(Class<?> type, Class<?> superTypeToResolve) {
        Type supertype = type.getGenericSuperclass();
        Class<?> superclass = type.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            if (supertype instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) supertype;
                if (pt.getRawType() == superTypeToResolve) {
                    return resolveTypeArguments(supertype);
                }
            }

            supertype = superclass.getGenericSuperclass();
            superclass = superclass.getSuperclass();
        }
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    /**
     * Resolves a single generic type argument from the super class of the given type.
     *
     * @param type The type to resolve from
     * @return A single Class or null
     */
    public static Optional<Class> resolveSuperGenericTypeArgument(Class type) {
        try {
            Type genericSuperclass = type.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                return resolveSingleTypeArgument(genericSuperclass);
            }
            return Optional.empty();
        } catch (NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the type arguments for a generic type.
     *
     * @param genericType The generic type
     * @return The type arguments
     */
    public static Class[] resolveTypeArguments(Type genericType) {
        Class[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            typeArguments = resolveParameterizedType(pt);
        }
        return typeArguments;
    }

    /**
     * Resolves a single type argument from the given interface of the given class. Also
     * searches superclasses.
     *
     * @param type          The type to resolve from
     * @param interfaceType The interface to resolve for
     * @return The class or null
     */
    public static Optional<Class> resolveInterfaceTypeArgument(Class type, Class interfaceType) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if (pt.getRawType() == interfaceType) {
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
     * Resolve a single type from the given generic type.
     *
     * @param genericType The generic type
     * @return An {@link Optional} of the type
     */
        private static Optional<Class> resolveSingleTypeArgument(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                Type actualTypeArgument = actualTypeArguments[0];
                return resolveParameterizedTypeArgument(actualTypeArgument);
            }
        }
        return Optional.empty();
    }

    /**
     * @param actualTypeArgument The actual type argument
     * @return An optional with the resolved parameterized class
     */
    private static Optional<Class> resolveParameterizedTypeArgument(Type actualTypeArgument) {
        ParameterizedType pt;
        if (actualTypeArgument instanceof Class) {
            return Optional.of((Class) actualTypeArgument);
        }
        if (actualTypeArgument instanceof ParameterizedType) {
            pt = (ParameterizedType) actualTypeArgument;
            Type rawType = pt.getRawType();
            if (rawType instanceof Class) {
                return Optional.of((Class) rawType);
            }
        }
        return Optional.empty();
    }

    /**
     * @param aClass A class
     * @return All generic interfaces
     */
    private static Set<Type> getAllGenericInterfaces(Class<?> aClass) {
        Set<Type> interfaces = new LinkedHashSet<>();
        return populateInterfaces(aClass, interfaces);
    }

    /**
     * @param aClass     Some class
     * @param interfaces The interfaces
     * @return A set of interfaces
     */
    @SuppressWarnings("Duplicates")
    private static Set<Type> populateInterfaces(Class<?> aClass, Set<Type> interfaces) {
        Type[] theInterfaces = aClass.getGenericInterfaces();
        interfaces.addAll(Arrays.asList(theInterfaces));
        for (Type theInterface : theInterfaces) {
            if (theInterface instanceof Class) {
                Class<?> i = (Class<?>) theInterface;
                if (ArrayUtils.isNotEmpty(i.getGenericInterfaces())) {
                    populateInterfaces(i, interfaces);
                }
            }
        }
        if (!aClass.isInterface()) {
            Class<?> superclass = aClass.getSuperclass();
            while (superclass != null) {
                populateInterfaces(superclass, interfaces);
                superclass = superclass.getSuperclass();
            }
        }
        return interfaces;
    }

    private static Class[] resolveParameterizedType(ParameterizedType pt) {
        Class[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        Type[] actualTypeArguments = pt.getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            typeArguments = new Class[actualTypeArguments.length];
            for (int i = 0; i < actualTypeArguments.length; i++) {
                Type actualTypeArgument = actualTypeArguments[i];
                Optional<Class> opt = resolveParameterizedTypeArgument(actualTypeArgument);
                if (opt.isPresent()) {
                    typeArguments[i] = opt.get();
                } else {
                    typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
                    break;
                }
            }
        }
        return typeArguments;
    }
}
