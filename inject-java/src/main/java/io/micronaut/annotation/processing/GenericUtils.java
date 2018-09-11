/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.annotation.processing;

import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.VOID;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    GenericUtils(Elements elementUtils, Types typeUtils, ModelUtils modelUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.modelUtils = modelUtils;
    }

    /**
     * Finds the generic type for the given interface for the given class element.
     * <p>
     * For example, for <code>class AProvider implements Provider</code>
     * element = AProvider
     * interfaceType = interface javax.inject.Provider.class
     * return A
     *
     * @param element       The class element
     * @param interfaceType The interface
     * @return The generic type or null
     */
    protected TypeMirror interfaceGenericTypeFor(TypeElement element, Class interfaceType) {
        return interfaceGenericTypeFor(element, interfaceType.getName());
    }

    /**
     * Finds the generic type for the given interface for the given class element.
     * <p>
     * For example, for <code>class AProvider implements Provider&lt;A&gt;</code>
     * element = AProvider
     * interfaceName = interface javax.inject.Provider
     * return A
     *
     * @param element       The class element
     * @param interfaceName The interface
     * @return The generic type or null
     */
    protected TypeMirror interfaceGenericTypeFor(TypeElement element, String interfaceName) {
        List<? extends TypeMirror> typeMirrors = interfaceGenericTypesFor(element, interfaceName);
        return typeMirrors.isEmpty() ? null : typeMirrors.get(0);
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
     * @param type       The type mirror
     * @param boundTypes The bound types (such as those declared on the class)
     * @return A map of generic type arguments
     */
    protected Map<String, Object> resolveGenericTypes(TypeMirror type, Map<String, Object> boundTypes) {
        if (type.getKind().isPrimitive() || type.getKind() == VOID || type.getKind() == ARRAY) {
            return Collections.emptyMap();
        }
        if (type instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) type;
            return resolveGenericTypes(declaredType, (TypeElement) declaredType.asElement(), boundTypes);
        } else if (type instanceof TypeVariable) {
            TypeVariable var = (TypeVariable) type;
            TypeMirror upperBound = var.getUpperBound();
            if (upperBound instanceof DeclaredType) {
                return resolveGenericTypes(upperBound, boundTypes);
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Resolve the generic type arguments for the given type mirror and bound type arguments.
     *
     * @param type        The declaring type
     * @param typeElement The type element
     * @param boundTypes  The bound types
     * @return A map of generic type arguments
     */
    protected Map<String, Object> resolveGenericTypes(DeclaredType type, TypeElement typeElement, Map<String, Object> boundTypes) {
        List<? extends TypeMirror> typeArguments = type.getTypeArguments();
        Map<String, Object> resolvedParameters = new LinkedHashMap<>();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        if (typeArguments.size() == typeParameters.size()) {
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                String parameterName = typeParameter.toString();
                TypeMirror mirror = i.next();

                TypeKind kind = mirror.getKind();
                switch (kind) {
                    case TYPEVAR:
                        TypeVariable tv = (TypeVariable) mirror;
                        if (boundTypes.containsKey(tv.toString())) {
                            resolvedParameters.put(parameterName, boundTypes.get(tv.toString()));
                        } else {
                            TypeMirror upperBound = tv.getUpperBound();
                            TypeMirror lowerBound = tv.getLowerBound();
                            if (upperBound.getKind() != TypeKind.NULL) {
                                resolvedParameters.put(parameterName, resolveTypeReference(upperBound, Collections.emptyMap()));
                            } else if (lowerBound.getKind() != TypeKind.NULL) {
                                resolvedParameters.put(parameterName, resolveTypeReference(lowerBound, Collections.emptyMap()));
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
                        resolveGenericTypeParameterForPrimitiveOrArray(resolvedParameters, parameterName, mirror, boundTypes);
                        continue;
                    case DECLARED:
                        resolveGenericTypeParameter(resolvedParameters, parameterName, mirror, boundTypes);
                        continue;
                    case WILDCARD:
                        WildcardType wcType = (WildcardType) mirror;
                        TypeMirror extendsBound = wcType.getExtendsBound();
                        TypeMirror superBound = wcType.getSuperBound();
                        if (extendsBound != null) {
                            resolveGenericTypeParameter(resolvedParameters, parameterName, extendsBound, boundTypes);
                        } else if (superBound != null) {
                            resolveGenericTypeParameter(resolvedParameters, parameterName, superBound, boundTypes);
                        } else {
                            resolvedParameters.put(parameterName, Object.class);
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
    protected Object resolveTypeReference(TypeMirror mirror) {
        return resolveTypeReference(mirror, Collections.emptyMap());
    }

    /**
     * Resolve a type reference to use for the given type mirror taking into account generic type variables.
     *
     * @param mirror     The mirror
     * @param boundTypes The already bound types for any type variable
     * @return A type reference
     */
    protected Object resolveTypeReference(TypeMirror mirror, Map<String, Object> boundTypes) {
        TypeKind kind = mirror.getKind();
        switch (kind) {
            case TYPEVAR:
                TypeVariable tv = (TypeVariable) mirror;
                String name = tv.toString();
                if (boundTypes.containsKey(name)) {
                    return boundTypes.get(name);
                } else {
                    return modelUtils.resolveTypeReference(mirror);
                }
            case WILDCARD:
                WildcardType wcType = (WildcardType) mirror;
                TypeMirror extendsBound = wcType.getExtendsBound();
                TypeMirror superBound = wcType.getSuperBound();
                if (extendsBound == null && superBound == null) {
                    return Object.class.getName();
                } else if (extendsBound != null) {
                    return resolveTypeReference(typeUtils.erasure(extendsBound), boundTypes);
                } else if (superBound != null) {
                    return resolveTypeReference(superBound, boundTypes);
                } else {
                    return resolveTypeReference(typeUtils.getWildcardType(extendsBound, superBound), boundTypes);
                }
            case ARRAY:
                ArrayType arrayType = (ArrayType) mirror;
                Object reference = resolveTypeReference(arrayType.getComponentType(), boundTypes);
                if (reference instanceof Class) {
                    Class componentType = (Class) reference;
                    return Array.newInstance(componentType, 0).getClass();
                } else if (reference instanceof String) {
                    return reference + "[]";
                } else {
                    return modelUtils.resolveTypeReference(mirror);
                }
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case DOUBLE:
            case FLOAT:
            case INT:
            case LONG:
            case SHORT:
                Optional<Class> type = ClassUtils.forName(mirror.toString(), getClass().getClassLoader());
                if (type.isPresent()) {
                    return type.get();
                } else {
                    throw new IllegalStateException("Unknown primitive type: " + mirror.toString());
                }
            default:
                return modelUtils.resolveTypeReference(mirror);
        }
    }

    /**
     * Resolve the first type argument to a parameterized type.
     *
     * @param element      The type element
     * @param typeVariable The type variable
     * @return The declaring type
     */
    protected DeclaredType resolveTypeVariable(Element element, TypeVariable typeVariable) {
        Element enclosing = element.getEnclosingElement();

        while (enclosing instanceof Parameterizable) {
            Parameterizable parameterizable = (Parameterizable) enclosing;
            String name = typeVariable.toString();
            for (TypeParameterElement typeParameter : parameterizable.getTypeParameters()) {
                if (name.equals(typeParameter.toString())) {
                    List<? extends TypeMirror> bounds = typeParameter.getBounds();
                    if (bounds.size() == 1) {
                        TypeMirror typeMirror = bounds.get(0);
                        if (typeMirror.getKind() == TypeKind.DECLARED) {
                            return (DeclaredType) typeMirror;
                        }
                    }
                }
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return null;
    }

    /**
     * Resolve bound types for the given declared type.
     *
     * @param type The declaring type
     * @return The type bounds
     */
    protected Map<String, Object> resolveBoundTypes(DeclaredType type) {
        Map<String, Object> boundTypes = new LinkedHashMap<>(2);
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

    private void resolveGenericTypeParameter(Map<String, Object> resolvedParameters, String parameterName, TypeMirror mirror, Map<String, Object> boundTypes) {
        if (mirror instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) mirror;
            List<? extends TypeMirror> nestedArguments = declaredType.getTypeArguments();
            if (nestedArguments.isEmpty()) {
                resolvedParameters.put(
                        parameterName,
                        resolveTypeReference(typeUtils.erasure(mirror), resolvedParameters)
                );
            } else {
                resolvedParameters.put(
                        parameterName,
                        Collections.singletonMap(
                                resolveTypeReference(typeUtils.erasure(mirror), resolvedParameters),
                                resolveGenericTypes(declaredType, boundTypes)
                        )
                );
            }
        } else if (mirror instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) mirror;
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

    private void resolveGenericTypeParameterForPrimitiveOrArray(Map<String, Object> resolvedParameters, String parameterName, TypeMirror mirror, Map<String, Object> boundTypes) {
        resolvedParameters.put(
            parameterName,
            Collections.singletonMap(
                resolveTypeReference(typeUtils.erasure(mirror), resolvedParameters),
                resolveGenericTypes(mirror, boundTypes)
            )
        );
    }
}
