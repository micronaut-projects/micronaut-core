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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;

/**
 * Utility methods for dealing with generic type signatures.
 *
 * @author Graeme Rocher
 */
@Internal
public class GenericUtils {

    private final Elements elementUtils;
    private final Types typeUtils;
    private final ModelUtils modelUtils;

    /**
     * @param elementUtils The {@link Elements}
     * @param typeUtils    The {@link Types}
     * @param modelUtils   The {@link ModelUtils}
     */
    protected GenericUtils(Elements elementUtils, Types typeUtils, ModelUtils modelUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.modelUtils = modelUtils;
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param dt The declared type
     * @return The type argument information
     */
    Map<String, Map<String, TypeMirror>> buildGenericTypeArgumentInfo(DeclaredType dt) {
        Element element = dt.asElement();
        return buildGenericTypeArgumentInfo(element, dt, Collections.emptyMap());
    }

    private Map<String, Map<String, TypeMirror>> buildGenericTypeArgumentInfo(@NonNull Element element, @Nullable DeclaredType dt, Map<String, TypeMirror> boundTypes) {

        Map<String, Map<String, TypeMirror>> beanTypeArguments = new LinkedHashMap<>();
        if (dt != null) {

            List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                TypeElement typeElement = (TypeElement) element;

                Map<String, TypeMirror> directTypeArguments = resolveBoundTypes(dt);
                if (CollectionUtils.isNotEmpty(directTypeArguments)) {
                    beanTypeArguments.put(typeElement.getQualifiedName().toString(), directTypeArguments);
                }
            }
        }

        if (element instanceof TypeElement typeElement) {
            if (CollectionUtils.isNotEmpty(boundTypes)) {
                beanTypeArguments.put(JavaModelUtils.getClassName(typeElement), boundTypes);
            }
            populateTypeArguments(typeElement, beanTypeArguments);
        }
        return beanTypeArguments;
    }

    /**
     * Finds the generic types for the given interface for the given class element.
     *
     * @param element       The class element
     * @param interfaceName The interface
     * @return The generic types or an empty list
     */
    public List<? extends TypeMirror> interfaceGenericTypesFor(TypeElement element, String interfaceName) {
        for (TypeMirror tm : element.getInterfaces()) {
            DeclaredType declaredType = (DeclaredType) tm;
            Element declaredElement = declaredType.asElement();
            if (declaredElement instanceof TypeElement te) {
                if (interfaceName.equals(te.getQualifiedName().toString())) {
                    return declaredType.getTypeArguments();
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Resolve the generic type arguments for the given type mirror and bound type arguments.
     *
     * @param type        The declaring type
     * @param typeElement The type element
     * @param boundTypes  The bound types
     * @return A map of generic type arguments
     */
    private Map<String, TypeMirror> resolveGenericTypes(DeclaredType type, TypeElement typeElement, Map<String, TypeMirror> boundTypes) {
        List<? extends TypeMirror> typeArguments = type.getTypeArguments();
        Map<String, TypeMirror> resolvedParameters = new LinkedHashMap<>();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        if (typeArguments.size() == typeParameters.size()) {
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                String parameterName = typeParameter.toString();
                TypeMirror mirror = i.next();

                TypeKind kind = mirror.getKind();
                switch (kind) {
                    case DECLARED:
                        resolvedParameters.put(parameterName, mirror);
                    break;
                    case TYPEVAR:
                        TypeVariable tv = (TypeVariable) mirror;
                        if (boundTypes.containsKey(tv.toString())) {
                            resolvedParameters.put(parameterName, boundTypes.get(tv.toString()));
                        } else {
                            TypeMirror upperBound = tv.getUpperBound();
                            TypeMirror lowerBound = tv.getLowerBound();
                            if (upperBound.getKind() != TypeKind.NULL) {
                                resolvedParameters.put(parameterName, resolveTypeReference(upperBound, boundTypes));
                            } else if (lowerBound.getKind() != TypeKind.NULL) {
                                resolvedParameters.put(parameterName, resolveTypeReference(lowerBound, boundTypes));
                            }
                        }
                        continue;
                    case ARRAY:
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case DOUBLE:
                    case FLOAT:
                    case INT:
                    case LONG:
                    case SHORT:
                        resolveGenericTypeParameter(resolvedParameters, parameterName, mirror, boundTypes);
                    continue;
                    case WILDCARD:
                        WildcardType wcType = (WildcardType) mirror;
                        TypeMirror extendsBound = wcType.getExtendsBound();
                        TypeMirror superBound = wcType.getSuperBound();
                        if (extendsBound != null) {
                            resolveGenericTypeParameter(resolvedParameters, parameterName, extendsBound, boundTypes);
                        } else if (superBound != null) {
                            if (superBound instanceof TypeVariable superTypeVar) {
                                final TypeMirror upperBound = superTypeVar.getUpperBound();
                                if (upperBound != null && !type.equals(upperBound)) {
                                    resolveGenericTypeParameter(resolvedParameters, parameterName, superBound, boundTypes);
                                }
                            } else {
                                resolveGenericTypeParameter(resolvedParameters, parameterName, superBound, boundTypes);
                            }
                        } else {
                            resolvedParameters.put(parameterName, elementUtils.getTypeElement(Object.class.getName()).asType());
                        }
                        break;
                    default:
                        // no-op
                }
            }
        }
        return resolvedParameters;
    }

    /**
     * Resolve a type reference to use for the given type mirror taking into account generic type variables.
     *
     * @param mirror     The mirror
     * @param boundTypes The already bound types for any type variable
     * @return A type reference
     */
    protected TypeMirror resolveTypeReference(TypeMirror mirror, Map<String, TypeMirror> boundTypes) {
        TypeKind kind = mirror.getKind();
        switch (kind) {
            case TYPEVAR:
                TypeVariable tv = (TypeVariable) mirror;
                String name = tv.toString();
                if (boundTypes.containsKey(name)) {
                    return boundTypes.get(name);
                } else {
                    return resolveTypeReference(tv.getUpperBound(), boundTypes);
                }
            case WILDCARD:
                WildcardType wcType = (WildcardType) mirror;
                TypeMirror extendsBound = wcType.getExtendsBound();
                TypeMirror superBound = wcType.getSuperBound();
                if (extendsBound == null && superBound == null) {
                    return elementUtils.getTypeElement(Object.class.getName()).asType();
                } else if (extendsBound != null) {
                    return resolveTypeReference(typeUtils.erasure(extendsBound), boundTypes);
                } else {
                    return resolveTypeReference(superBound, boundTypes);
                }
            case ARRAY:
                ArrayType arrayType = (ArrayType) mirror;
                TypeMirror reference = resolveTypeReference(arrayType.getComponentType(), boundTypes);
                return typeUtils.getArrayType(reference);
            default:
                return modelUtils.resolveTypeReference(mirror);
        }
    }

    /**
     * Resolve bound types for the given declared type.
     *
     * @param type The declaring type
     * @return The type bounds
     */
    protected Map<String, TypeMirror> resolveBoundTypes(DeclaredType type) {
        Map<String, TypeMirror> boundTypes = new LinkedHashMap<>(2);
        TypeElement element = (TypeElement) type.asElement();

        List<? extends TypeParameterElement> typeParameters = element.getTypeParameters();
        List<? extends TypeMirror> typeArguments = type.getTypeArguments();
        if (typeArguments.size() == typeParameters.size()) {
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                boundTypes.put(typeParameter.toString(), resolveTypeReference(i.next(), boundTypes));
            }
        }

        return boundTypes;
    }

    private void resolveGenericTypeParameter(Map<String, TypeMirror> resolvedParameters, String parameterName, TypeMirror mirror, Map<String, TypeMirror> boundTypes) {
        if (mirror instanceof DeclaredType) {
            resolvedParameters.put(
                    parameterName,
                    mirror
            );
        } else if (mirror instanceof TypeVariable tv) {
            String variableName = tv.toString();
            if (boundTypes.containsKey(variableName)) {
                resolvedParameters.put(
                        parameterName,
                        boundTypes.get(variableName)
                );
            } else {
                TypeMirror upperBound = tv.getUpperBound();
                if (upperBound instanceof DeclaredType) {
                    resolveGenericTypeParameter(
                            resolvedParameters,
                            parameterName,
                            upperBound,
                            boundTypes
                    );
                }
            }
        }
    }

    private void populateTypeArguments(TypeElement typeElement, Map<String, Map<String, TypeMirror>> typeArguments) {
        TypeElement current = typeElement;
        while (current != null) {

            populateTypeArgumentsForInterfaces(typeArguments, current);
            TypeMirror superclass = current.getSuperclass();

            if (superclass.getKind() == TypeKind.NONE) {
                current = null;
            } else {
                if (superclass instanceof DeclaredType dt) {
                    List<? extends TypeMirror> superArguments = dt.getTypeArguments();


                    Element te = dt.asElement();
                    if (te instanceof TypeElement element) {
                        TypeElement child = current;
                        current = element;
                        if (CollectionUtils.isNotEmpty(superArguments)) {
                            Map<String, TypeMirror> boundTypes = typeArguments.get(JavaModelUtils.getClassName(child));
                            if (boundTypes != null) {
                                Map<String, TypeMirror> types = resolveGenericTypes(dt, current, boundTypes);

                                String name = JavaModelUtils.getClassName(current);
                                typeArguments.put(name, types);
                            } else {
                                List<? extends TypeParameterElement> typeParameters = current.getTypeParameters();
                                Map<String, TypeMirror> types = new LinkedHashMap<>(typeParameters.size());
                                if (typeParameters.size() == superArguments.size()) {
                                    Iterator<? extends TypeMirror> i = superArguments.iterator();
                                    for (TypeParameterElement typeParameter : typeParameters) {
                                        String n = typeParameter.getSimpleName().toString();
                                        types.put(n, i.next());
                                    }
                                }
                                typeArguments.put(JavaModelUtils.getClassName(current), types);
                            }
                        }

                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
    }

    private void populateTypeArgumentsForInterfaces(Map<String, Map<String, TypeMirror>> typeArguments, TypeElement child) {
        for (TypeMirror anInterface : child.getInterfaces()) {
            if (anInterface instanceof DeclaredType declaredType) {
                Element element = declaredType.asElement();
                if (element instanceof TypeElement te) {
                    String name = JavaModelUtils.getClassName(te);
                    if (!typeArguments.containsKey(name)) {
                        Map<String, TypeMirror> boundTypes = typeArguments.get(JavaModelUtils.getClassName(child));
                        if (boundTypes == null) {
                            boundTypes = Collections.emptyMap();
                        }
                        Map<String, TypeMirror> types = resolveGenericTypes(declaredType, te, boundTypes);
                        if (!types.isEmpty()) {
                            typeArguments.put(name, types);
                        }
                    }
                    populateTypeArgumentsForInterfaces(typeArguments, te);
                }
            }
        }
    }
}
