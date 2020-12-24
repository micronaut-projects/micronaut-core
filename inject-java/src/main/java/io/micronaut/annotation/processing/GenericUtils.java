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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;

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
        return buildGenericTypeArgumentInfo(element, dt);
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param element The element
     * @return The type argument information
     */
    public Map<String, Map<String, TypeMirror>> buildGenericTypeArgumentElementInfo(@NonNull Element element) {
        return buildGenericTypeArgumentElementInfo(element, null);
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param element The element
     * @param declaredType The declared type
     * @return The type argument information
     */
    public Map<String, Map<String, TypeMirror>> buildGenericTypeArgumentElementInfo(@NonNull Element element, @Nullable DeclaredType declaredType) {
        return buildGenericTypeArgumentInfo(element, declaredType);
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param element The element
     * @param dt The declared type
     * @return The type argument information
     */
    private Map<String, Map<String, TypeMirror>> buildGenericTypeArgumentInfo(@NonNull Element element, @Nullable DeclaredType dt) {

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

        if (element instanceof TypeElement) {
            TypeElement typeElement = (TypeElement) element;
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
            if (declaredElement instanceof TypeElement) {
               TypeElement te = (TypeElement) declaredElement;
                if (interfaceName.equals(te.getQualifiedName().toString())) {
                    return declaredType.getTypeArguments();
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Return the first type argument for the given type mirror. For example for Optional&lt;String&gt; this will
     * return {@code String}.
     *
     * @param type The type
     * @return The first argument.
     */
    protected Optional<TypeMirror> getFirstTypeArgument(TypeMirror type) {
        TypeMirror typeMirror = null;

        if (type instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) type;
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                typeMirror = typeArguments.get(0);
            }
        }
        return Optional.ofNullable(typeMirror);
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
                            if (superBound instanceof TypeVariable) {
                                TypeVariable superTypeVar = (TypeVariable) superBound;
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
                    default:
                        // no-op
                }
            }
        }
        return resolvedParameters;
    }

    /**
     * @param mirror The {@link TypeMirror}
     * @return The resolved type reference
     */
    protected TypeMirror resolveTypeReference(TypeMirror mirror) {
        return resolveTypeReference(mirror, Collections.emptyMap());
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

    /**
     * Takes a type element and the bound generic information and re-aligns for the new type.
     *
     * @param typeElement The type element
     * @param typeArguments The type arguments
     * @param genericsInfo The generic info
     * @return The aligned generics
     */
    public Map<String, Map<String, TypeMirror>> alignNewGenericsInfo(
            TypeElement typeElement,
            List<? extends TypeMirror> typeArguments,
            Map<String, TypeMirror> genericsInfo) {
        String typeName = typeElement.getQualifiedName().toString();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        Map<String, TypeMirror> resolved = alignNewGenericsInfo(typeParameters, typeArguments, genericsInfo);
        if (!resolved.isEmpty()) {
            return Collections.singletonMap(
                    typeName,
                    resolved
            );
        }
        return Collections.emptyMap();
    }

    /**
     * Takes the bound generic information and re-aligns for the new type.
     *
     * @param typeParameters The type parameters
     * @param typeArguments The type arguments
     * @param genericsInfo The generic info
     * @return The aligned generics
     */
    public Map<String, TypeMirror> alignNewGenericsInfo(
            List<? extends TypeParameterElement> typeParameters,
            List<? extends TypeMirror> typeArguments,
            Map<String, TypeMirror> genericsInfo) {
        if (typeArguments.size() == typeParameters.size()) {

            Map<String, TypeMirror> resolved = new HashMap<>(typeArguments.size());
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                TypeMirror typeParameterMirror = i.next();
                String variableName = typeParameter.getSimpleName().toString();
                resolveVariableForMirror(genericsInfo, resolved, variableName, typeParameterMirror);
            }
            return resolved;
        }
        return Collections.emptyMap();
    }

    private void resolveVariableForMirror(
            Map<String, TypeMirror> genericsInfo,
            Map<String, TypeMirror> resolved,
            String variableName,
            TypeMirror mirror) {
        if (mirror instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) mirror;
            resolveTypeVariable(genericsInfo, resolved, variableName, tv);
        } else {
            if (mirror instanceof WildcardType) {
                WildcardType wt = (WildcardType) mirror;
                TypeMirror extendsBound = wt.getExtendsBound();
                if (extendsBound != null) {
                    resolveVariableForMirror(genericsInfo, resolved, variableName, extendsBound);
                } else {
                    TypeMirror superBound = wt.getSuperBound();
                    resolveVariableForMirror(genericsInfo, resolved, variableName, superBound);
                }
            } else if (mirror instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) mirror;
                List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
                if (CollectionUtils.isNotEmpty(typeArguments) && CollectionUtils.isNotEmpty(genericsInfo)) {
                    List<TypeMirror> resolvedArguments = new ArrayList<>(typeArguments.size());
                    for (TypeMirror typeArgument : typeArguments) {
                        if (typeArgument instanceof TypeVariable) {
                            TypeVariable tv = (TypeVariable) typeArgument;
                            String name = tv.toString();
                            TypeMirror bound = genericsInfo.get(name);
                            if (bound != null) {
                                resolvedArguments.add(bound);
                            } else {
                                resolvedArguments.add(typeArgument);
                            }
                        } else {
                            resolvedArguments.add(typeArgument);
                        }
                    }
                    TypeMirror[] typeMirrors = resolvedArguments.toArray(new TypeMirror[0]);
                    resolved.put(variableName, typeUtils.getDeclaredType((TypeElement) dt.asElement(), typeMirrors));
                } else {
                    resolved.put(variableName, mirror);
                }
            } else if (mirror instanceof ArrayType) {
                resolved.put(variableName, mirror);
            }
        }
    }

    private void resolveTypeVariable(
            Map<String, TypeMirror> genericsInfo,
            Map<String, TypeMirror> resolved,
            String variableName,
            TypeVariable variable) {
        String name = variable.toString();
        TypeMirror element = genericsInfo.get(name);
        if (element != null) {
            if (element instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) element;
                List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
                for (TypeMirror typeArgument : typeArguments) {
                    if (typeArgument instanceof TypeVariable) {
                        TypeVariable tv = (TypeVariable) typeArgument;
                        TypeMirror upperBound = tv.getUpperBound();
                        if (upperBound instanceof DeclaredType) {
                            resolved.put(variableName, upperBound);
                            break;
                        }

                        TypeMirror lowerBound = tv.getLowerBound();
                        if (lowerBound instanceof DeclaredType) {
                            resolved.put(variableName, lowerBound);
                            break;
                        }
                    }
                }

                if (!resolved.containsKey(variableName)) {
                    resolved.put(variableName, element);
                }
            } else {
                resolved.put(variableName, element);
            }
        } else {
            TypeMirror upperBound = variable.getUpperBound();
            if (upperBound instanceof TypeVariable) {
                resolveTypeVariable(genericsInfo, resolved, variableName, (TypeVariable) upperBound);
            } else if (upperBound instanceof DeclaredType) {
                resolved.put(
                        variableName,
                        upperBound
                );
            } else {
                TypeMirror lowerBound = variable.getLowerBound();
                if (lowerBound instanceof TypeVariable) {
                    resolveTypeVariable(genericsInfo, resolved, variableName, (TypeVariable) lowerBound);
                } else if (lowerBound instanceof DeclaredType) {
                    resolved.put(
                            variableName,
                            lowerBound
                    );
                }
            }
        }
    }

    private void resolveGenericTypeParameter(Map<String, TypeMirror> resolvedParameters, String parameterName, TypeMirror mirror, Map<String, TypeMirror> boundTypes) {
        if (mirror instanceof DeclaredType) {
            resolvedParameters.put(
                    parameterName,
                    mirror
            );
        } else if (mirror instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) mirror;
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
                if (superclass instanceof DeclaredType) {
                    DeclaredType dt = (DeclaredType) superclass;
                    List<? extends TypeMirror> superArguments = dt.getTypeArguments();


                    Element te = dt.asElement();
                    if (te instanceof TypeElement) {
                        TypeElement child = current;
                        current = (TypeElement) te;
                        if (CollectionUtils.isNotEmpty(superArguments)) {
                            Map<String, TypeMirror> boundTypes = typeArguments.get(child.getQualifiedName().toString());
                            if (boundTypes != null) {
                                Map<String, TypeMirror> types = resolveGenericTypes(dt, current, boundTypes);

                                String name = current.getQualifiedName().toString();
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
                                String name = current.getQualifiedName().toString();
                                typeArguments.put(name, types);
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
            if (anInterface instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType) anInterface;
                Element element = declaredType.asElement();
                if (element instanceof TypeElement) {
                    TypeElement te = (TypeElement) element;
                    String name = te.getQualifiedName().toString();
                    if (!typeArguments.containsKey(name)) {
                        Map<String, TypeMirror> boundTypes = typeArguments.get(child.getQualifiedName().toString());
                        if (boundTypes == null) {
                            boundTypes = Collections.emptyMap();
                        }
                        Map<String, TypeMirror> types = resolveGenericTypes(declaredType, te, boundTypes);
                        typeArguments.put(name, types);
                    }
                    populateTypeArgumentsForInterfaces(typeArguments, te);
                }
            }
        }
    }
}
