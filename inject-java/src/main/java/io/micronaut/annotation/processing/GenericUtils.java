/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.util.CollectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.reflect.Array;
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
    Map<String, Map<String, Object>> buildGenericTypeArgumentInfo(DeclaredType dt) {
        Element element = dt.asElement();
        return buildGenericTypeArgumentInfo(element, dt);
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param element The element
     * @return The type argument information
     */
    public Map<String, Map<String, Object>> buildGenericTypeArgumentInfo(@NonNull Element element) {
        return buildGenericTypeArgumentInfo(element, null);
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param element The element
     * @return The type argument information
     */
    public Map<String, Map<String, TypeMirror>> buildGenericTypeArgumentElementInfo(@NonNull Element element) {
        Map<String, Map<String, Object>> data = buildGenericTypeArgumentInfo(element, null);
        Map<String, Map<String, TypeMirror>> elements = new HashMap<>(data.size());
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            Map<String, Object> value = entry.getValue();
            HashMap<String, TypeMirror> submap = new HashMap<>(value.size());
            for (Map.Entry<String, Object> genericEntry : value.entrySet()) {
                TypeElement te = elementUtils.getTypeElement(genericEntry.getValue().toString());
                if (te != null) {
                    submap.put(genericEntry.getKey(), te.asType());
                }
            }
            elements.put(entry.getKey(), submap);
        }
        return elements;
    }

    /**
     * Builds type argument information for the given type.
     *
     * @param element The element
     * @param dt The declared type
     * @return The type argument information
     */
    private Map<String, Map<String, Object>> buildGenericTypeArgumentInfo(@NonNull Element element, @Nullable DeclaredType dt) {

        Map<String, Map<String, Object>> beanTypeArguments = new HashMap<>();
        if (dt != null) {

            List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                TypeElement typeElement = (TypeElement) element;

                Map<String, Object> directTypeArguments = resolveBoundTypes(dt);
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
                    return resolveTypeReference(tv.getUpperBound(), boundTypes);
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
                        TypeKind kind = typeMirror.getKind();
                        switch (kind) {
                            case DECLARED:
                                return (DeclaredType) typeMirror;
                            case TYPEVAR:
                                return resolveTypeVariable(element, (TypeVariable) typeMirror);
                           default:
                               return null;
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

    /**
     * Resolve bound types for the given return type.
     *
     * @param declaringType The declaring type
     * @param returnType The return type
     * @param genericsInfo The declaring generics info
     * @return The type bounds
     */
    protected Map<String, TypeMirror> resolveBoundGenerics(TypeElement declaringType, TypeMirror returnType, Map<String, Map<String, TypeMirror>> genericsInfo) {

        if (returnType instanceof NoType) {
            return Collections.emptyMap();
        } else if (returnType instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) returnType;
            Element e = dt.asElement();
            List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
            if (e instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) e;
                Map<String, TypeMirror> boundGenerics = resolveBoundGenerics(declaringType, genericsInfo);
                if (!modelUtils.resolveKind(typeElement, ElementKind.ENUM).isPresent()) {
                    return alignNewGenericsInfo(
                            typeElement.getTypeParameters(),
                            typeArguments,
                            boundGenerics);
                }
            }
        } else if (returnType instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) returnType;
            TypeMirror upperBound = tv.getUpperBound();
            Map<String, TypeMirror> boundGenerics = resolveBoundGenerics(declaringType, genericsInfo);

            TypeMirror bound = boundGenerics.get(tv.toString());
            if (bound != null) {
                return Collections.singletonMap(tv.toString(), bound);
            } else {

                Map<String, TypeMirror> generics = resolveBoundGenerics(declaringType, upperBound, genericsInfo);
                if (!generics.isEmpty()) {
                    return generics;
                } else {
                    return resolveBoundGenerics(declaringType, tv.getLowerBound(), genericsInfo);
                }
            }
        }

        return Collections.emptyMap();
    }

    private Map<String, TypeMirror> resolveBoundGenerics(TypeElement typeElement, Map<String, Map<String, TypeMirror>> genericsInfo) {
        String declaringTypeName = null;
        if (typeElement != null) {
            declaringTypeName = typeElement.getQualifiedName().toString();
        }
        Map<String, TypeMirror> boundGenerics = genericsInfo.get(declaringTypeName);
        if (boundGenerics == null) {
            boundGenerics = Collections.emptyMap();
        }
        return boundGenerics;
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
                resolveVariableForMirror(genericsInfo, resolved, variableName, extendsBound);
            } else if (mirror instanceof DeclaredType) {
                resolved.put(variableName, mirror);
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
            resolved.put(variableName, element);
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

    private void populateTypeArguments(TypeElement typeElement, Map<String, Map<String, Object>> typeArguments) {
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
                            Map<String, Object> boundTypes = typeArguments.get(child.getQualifiedName().toString());
                            if (boundTypes == null) {
                                boundTypes = Collections.emptyMap();
                            }
                            Map<String, Object> types = resolveGenericTypes(dt, current, boundTypes);

                            String name = current.getQualifiedName().toString();
                            typeArguments.put(name, types);
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

    private void populateTypeArgumentsForInterfaces(Map<String, Map<String, Object>> typeArguments, TypeElement child) {
        for (TypeMirror anInterface : child.getInterfaces()) {
            if (anInterface instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType) anInterface;
                Element element = declaredType.asElement();
                if (element instanceof TypeElement) {
                    TypeElement te = (TypeElement) element;
                    String name = te.getQualifiedName().toString();
                    if (!typeArguments.containsKey(name)) {
                        Map<String, Object> boundTypes = typeArguments.get(child.getQualifiedName().toString());
                        if (boundTypes == null) {
                            boundTypes = Collections.emptyMap();
                        }
                        Map<String, Object> types = resolveGenericTypes(declaredType, te, boundTypes);
                        typeArguments.put(name, types);
                    }
                    populateTypeArgumentsForInterfaces(typeArguments, te);
                }
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
