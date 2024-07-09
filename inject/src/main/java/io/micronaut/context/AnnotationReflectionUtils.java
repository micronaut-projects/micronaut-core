/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The annotation reflection utils.
 *
 * @author Denis Stepanov
 * @since 4.6.0
 */
@Internal
public final class AnnotationReflectionUtils {

    AnnotationReflectionUtils() {}

    /**
     * Find implementation as an argument with types and annotations.
     *
     * @param implementationType The implementation class of the interface
     * @param implementedType    The implementedType type - interface or an abstract class
     * @return The argument of the interface with types and annotations
     * @since 4.6
     */
    @Nullable
    public static Argument<?> findImplementationAsArgument(@NonNull Class<?> implementationType,
                                                           @NonNull Class<?> implementedType) {
        return findImplemented(implementationType, implementedType, Map.of());
    }

    @Nullable
    private static Argument<?> findResolvedImplementation(Class<?> implementationType,
                                                          Class<?> implementedType, // Interface or an abstract class
                                                          AnnotatedType annotatedType,
                                                          Map<String, AnnotatedType> resolvedVariables) {
        Argument<?> argument = argumentOf(annotatedType, implementedType, resolvedVariables);
        if (argument != null) {
            if (implementationType.getAnnotations().length > 0) {
                // Append implementation annotations if any
                argument = Argument.of(
                    argument.getType(),
                    new AnnotationMetadataHierarchy(argument.getAnnotationMetadata(), annotationMetadataOf(implementationType)),
                    argument.getTypeParameters());
            }
        }
        return argument;
    }

    private static Argument<?> findImplemented(Class<?> implementationType,
                                               Class<?> implementedType,
                                               Map<String, AnnotatedType> resolvedVariables) {
        if (!implementedType.isAssignableFrom(implementationType)) {
            return null;
        }

        Class<?> superClass = implementationType.getSuperclass();
        if (superClass != null && !Object.class.equals(superClass)) {
            AnnotatedType annotatedSuperclass = implementationType.getAnnotatedSuperclass();
            Argument<?> resolvedImplementation;
            if (implementedType.equals(superClass)) {
                resolvedImplementation = findResolvedImplementation(implementationType, implementedType, annotatedSuperclass, resolvedVariables);
            } else {
                resolvedImplementation = findImplemented(superClass, implementedType, resolvedVariables(annotatedSuperclass, superClass));
            }
            if (resolvedImplementation != null) {
                return resolvedImplementation;
            }
        }
        Class<?>[] interfaces = implementationType.getInterfaces();
        AnnotatedType[] annotatedInterfaces = implementationType.getAnnotatedInterfaces();
        for (int i = 0; i < annotatedInterfaces.length; i++) {
            AnnotatedType annotatedInterface = annotatedInterfaces[i];
            Class<?> classFromType = getClassFromType(annotatedInterface.getType());
            Argument<?> resolvedImplementation;
            if (implementedType.equals(classFromType)) {
                resolvedImplementation = findResolvedImplementation(implementationType, implementedType, annotatedInterface, resolvedVariables);
            } else {
                resolvedImplementation = findImplemented(interfaces[i], implementedType, resolvedVariables(annotatedInterface, interfaces[0]));
            }
            if (resolvedImplementation != null) {
                return resolvedImplementation;
            }
        }
        return null;
    }

    private static Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?> classType) {
            return classType;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return getClassFromType(parameterizedType.getRawType());
        }
        if (type instanceof GenericArrayType) {
            return Object[].class;
        }
        if (type instanceof WildcardType wildcardType) {
            return getClassFromType(wildcardType.getUpperBounds()[0]);
        }
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    private static Map<String, AnnotatedType> resolvedVariables(AnnotatedType annotatedType, Class<?> type) {
        if (annotatedType instanceof AnnotatedParameterizedType parameterizedType) {
            AnnotatedType[] actualTypeArguments = parameterizedType.getAnnotatedActualTypeArguments();
            TypeVariable<? extends Class<?>>[] typeParameters = type.getTypeParameters();
            if (actualTypeArguments.length != typeParameters.length) {
                return Map.of();
            }
            Map<String, AnnotatedType> resolvedVariables = new LinkedHashMap<>();
            for (int i = 0; i < actualTypeArguments.length; i++) {
                AnnotatedType actualTypeArgument = actualTypeArguments[i];
                if (actualTypeArgument.getType() instanceof TypeVariable<?>) {
                    // Avoid unresolved type variables
                    continue;
                }
                resolvedVariables.put(
                    typeParameters[i].getName(),
                    actualTypeArgument
                );
            }
            return resolvedVariables;
        }
        return Map.of();
    }

    @Nullable
    private static Argument<?> argumentOf(@NonNull AnnotatedType type,
                                          @NonNull Class<?> interfaceType,
                                          @NonNull Map<String, AnnotatedType> resolvedVariables) {
        if (type.getType() instanceof TypeVariable<?> typeVariable) {
            type = resolvedVariables.get(typeVariable.getName());
            if (type == null) {
                // Unresolved type variable
                return null;
            }
        }
        Class<?> resolvedType = getClassFromType(type.getType());
        if (type instanceof AnnotatedParameterizedType annotatedParameterizedType) {
            AnnotatedType[] annotatedActualTypeArguments = annotatedParameterizedType.getAnnotatedActualTypeArguments();
            TypeVariable<? extends Class<?>>[] typeParameters = interfaceType.getTypeParameters();
            if (annotatedActualTypeArguments.length != typeParameters.length) {
                throw new IllegalArgumentException("Annotated parameters must have the same number of type parameters");
            }
            List<Argument<?>> list = new ArrayList<>();
            for (int i = 0; i < annotatedActualTypeArguments.length; i++) {
                TypeVariable<? extends Class<?>> typeVariable = typeParameters[i];
                Class<?> variableBaseType = null;
                AnnotatedType resolvedVariable = resolvedVariables.get(typeVariable.getName());
                if (resolvedVariable == null) {
                    resolvedVariable = annotatedActualTypeArguments[i];
                    TypeVariable<? extends Class<?>> typeParameter = typeParameters[i];
                    variableBaseType = getClassFromType(typeParameter.getBounds()[0]);
                }
                if (resolvedVariable instanceof TypeVariable<?>) {
                    // Found unresolved type variable
                    return null;
                }
                Argument<?> argument = argumentOf(resolvedVariable, variableBaseType, resolvedVariables);
                if (argument == null) {
                    return null;
                }
                list.add(Argument.ofTypeVariable(
                    argument.getType(),
                    typeVariable.getName(),
                    argument.getAnnotationMetadata(),
                    argument.getTypeParameters()
                ));
            }
            return Argument.of(
                resolvedType,
                annotationMetadataOf(type),
                list.toArray(new Argument<?>[0])
            );
        }
        return Argument.of(resolvedType, annotationMetadataOf(type));
    }

    private static AnnotationMetadata annotationMetadataOf(AnnotatedElement annotatedElement) {
        Annotation[] annotations = annotatedElement.getAnnotations();
        if (annotations.length == 0) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata mutableAnnotationMetadata = new MutableAnnotationMetadata();
        for (Annotation annotation : annotations) {
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            Class<? extends Annotation> annotationType = annotation.annotationType();
            Method[] methods = annotationType.getMethods();
            for (Method method : methods) {
                if (!method.getDeclaringClass().equals(annotationType)) {
                    continue;
                }
                Object value = ReflectionUtils.invokeMethod(annotation, method);
                if (value != null) {
                    values.put(method.getName(), value);
                }
            }
            mutableAnnotationMetadata.addAnnotation(annotationType.getName(), values);
        }
        return mutableAnnotationMetadata;
    }

}
