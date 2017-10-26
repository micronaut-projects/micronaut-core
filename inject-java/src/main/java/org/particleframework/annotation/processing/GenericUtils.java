package org.particleframework.annotation.processing;

import org.particleframework.core.reflect.ClassUtils;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.reflect.Array;
import java.util.*;

import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.type.TypeKind.WILDCARD;

class GenericUtils {

    private final Elements elementUtils;
    private final Types typeUtils;
    private final ModelUtils modelUtils;

    GenericUtils(Elements elementUtils, Types typeUtils, ModelUtils modelUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.modelUtils = modelUtils;
    }

    /**
     * Finds the generic type for the given interface for the given class element
     *
     *
     * For example, for <code>class AProvider implements Provider<A></code>
     *   element = AProvider
     *   interfaceType = interface javax.inject.Provider.class
     *   return A
     *
     * @param element The class element
     * @param interfaceType The interface
     * @return The generic type or null
     */
    TypeMirror interfaceGenericTypeFor(TypeElement element, Class interfaceType) {
        return interfaceGenericTypeFor(element, interfaceType.getName());
    }

    /**
     * Finds the generic type for the given interface for the given class element
     *
     *
     * For example, for <code>class AProvider implements Provider<A></code>
     *   element = AProvider
     *   interfaceName = interface javax.inject.Provider
     *   return A
     *
     * @param element The class element
     * @param interfaceName The interface
     * @return The generic type or null
     */
    TypeMirror interfaceGenericTypeFor(TypeElement element, String interfaceName) {
        List<? extends TypeMirror> typeMirrors = interfaceGenericTypesFor(element, interfaceName);
        return typeMirrors.isEmpty() ? null : typeMirrors.get(0);
    }

    /**
     * Finds the generic types for the given interface for the given class element
     *
     * @param element The class element
     * @param interfaceName The interface
     * @return The generic types or an empty list
     */
    List<? extends TypeMirror> interfaceGenericTypesFor(TypeElement element, String interfaceName) {
        for (TypeMirror tm: element.getInterfaces()) {
            DeclaredType declaredType = (DeclaredType) tm;
            TypeElement interfaceType = elementUtils.getTypeElement(typeUtils.erasure(declaredType).toString());
            if (interfaceName.equals(interfaceType.getQualifiedName().toString())) {
                return declaredType.getTypeArguments();
            }
        }
        return Collections.emptyList();
    }

    Map<String, Object> resolveGenericTypes(TypeMirror type) {
        if (type.getKind().isPrimitive() || type.getKind() == VOID || type.getKind() == ARRAY) {
            return Collections.emptyMap();
        }
        if(type instanceof DeclaredType) {

            DeclaredType declaredType = (DeclaredType) type;
            return resolveGenericTypes(declaredType, (TypeElement) declaredType.asElement());
        }
        else if(type instanceof TypeVariable) {
            TypeVariable var = (TypeVariable) type;
            TypeMirror upperBound = var.getUpperBound();
            if(upperBound instanceof DeclaredType) {
                return resolveGenericTypes(upperBound);
            }
        }
        return Collections.emptyMap();
    }


    Map<String, Object> resolveGenericTypes(DeclaredType type, TypeElement typeElement) {
        List<? extends TypeMirror> typeArguments = type.getTypeArguments();
        Map<String, Object> resolvedParameters = new LinkedHashMap<>();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        if(typeArguments.size() == typeParameters.size()) {
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                String parameterName = typeParameter.toString();
                TypeMirror mirror = i.next();

                TypeKind kind = mirror.getKind();
                switch (kind) {
                    case ARRAY:
                    case BOOLEAN:
                    case BYTE:
                    case CHAR:
                    case DOUBLE:
                    case FLOAT:
                    case INT:
                    case LONG:
                    case SHORT:
                        resolveGenericTypeParameterForPrimitiveOrArray(resolvedParameters, parameterName, mirror);
                    break;
                    case DECLARED:
                        resolveGenericTypeParameter(resolvedParameters, parameterName, mirror);
                    break;
                    case WILDCARD:
                        WildcardType wcType = (WildcardType) mirror;
                        TypeMirror extendsBound = wcType.getExtendsBound();
                        TypeMirror superBound = wcType.getSuperBound();
                        if (extendsBound != null) {
                            resolveGenericTypeParameter(resolvedParameters, parameterName, extendsBound);
                        }
                        else if (superBound != null) {
                            resolveGenericTypeParameter(resolvedParameters, parameterName, superBound);
                        }
                        else {
                            resolvedParameters.put(parameterName, Object.class);
                        }

                }
            }
        }
        return resolvedParameters;
    }

    private void resolveGenericTypeParameter(Map<String, Object> resolvedParameters, String parameterName, TypeMirror mirror) {
        DeclaredType declaredType = (DeclaredType)mirror;
        List<? extends TypeMirror> nestedArguments = declaredType.getTypeArguments();
        if(nestedArguments.isEmpty()) {
            resolvedParameters.put(
                    parameterName,
                    resolveTypeReference(typeUtils.erasure(mirror))
            );
        }
        else {
            resolvedParameters.put(
                    parameterName,
                    Collections.singletonMap(
                            resolveTypeReference(typeUtils.erasure(mirror)),
                            resolveGenericTypes(declaredType)
                    )
            );
        }
    }

    private void resolveGenericTypeParameterForPrimitiveOrArray(Map<String, Object> resolvedParameters, String parameterName, TypeMirror mirror) {
        resolvedParameters.put(
                parameterName,
                Collections.singletonMap(
                        resolveTypeReference(typeUtils.erasure(mirror)),
                        resolveGenericTypes(mirror)
                )
        );
    }

    Object resolveTypeReference(TypeMirror mirror) {
        TypeKind kind = mirror.getKind();
        switch (kind) {
            case WILDCARD:
                WildcardType wcType = (WildcardType) mirror;
                TypeMirror extendsBound = wcType.getExtendsBound();
                TypeMirror superBound = wcType.getSuperBound();
                if (extendsBound == null && superBound == null) {
                    return Object.class.getName();
                } else if (extendsBound != null) {
                    return resolveTypeReference(typeUtils.erasure(extendsBound));
                } else if (superBound != null) {
                    return resolveTypeReference(superBound);
                } else {
                    return resolveTypeReference(typeUtils.getWildcardType(extendsBound, superBound));
                }
            case ARRAY:
                ArrayType arrayType = (ArrayType) mirror;
                Object reference = resolveTypeReference(arrayType.getComponentType());
                if(reference instanceof Class) {
                    Class componentType = (Class) reference;
                    return Array.newInstance(componentType, 0).getClass();
                }
                else {
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
                if(type.isPresent()) {
                    return type.get();
                }
                else {
                    throw new IllegalStateException("Unknown primitive type");
                }
            default:
                return modelUtils.resolveTypeReference(mirror);
        }

    }

    public DeclaredType resolveTypeVariable(Element element, TypeVariable typeVariable) {
        Element enclosing = element.getEnclosingElement();

        while(enclosing != null && enclosing instanceof Parameterizable) {
            Parameterizable parameterizable = (Parameterizable) enclosing;
            String name = typeVariable.toString();
            for (TypeParameterElement typeParameter : parameterizable.getTypeParameters()) {
                if(name.equals(typeParameter.toString())) {
                    List<? extends TypeMirror> bounds = typeParameter.getBounds();
                    if(bounds.size() == 1) {
                        TypeMirror typeMirror = bounds.get(0);
                        if(typeMirror.getKind() == TypeKind.DECLARED) {
                            return (DeclaredType) typeMirror;
                        }
                    }
                }
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return null;
    }
}
