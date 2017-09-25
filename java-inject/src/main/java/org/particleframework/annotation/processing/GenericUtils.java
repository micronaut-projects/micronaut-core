package org.particleframework.annotation.processing;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.type.TypeKind.WILDCARD;

class GenericUtils {

    private final Elements elementUtils;
    private final Types typeUtils;

    GenericUtils(Elements elementUtils,Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
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

    // FIXME I don't know if this works correctly for all cases
    // Needs a test case. See InjectTransform.resolveGenericTypes
    // It doesn't get covered either.
    // test for FooBar<? extends Foo> and FooBar<? super Bar>
    List<Object> resolveGenericTypes(TypeMirror type) {
        if (type.getKind().isPrimitive() || type.getKind() == VOID || type.getKind() == ARRAY) {
            return Collections.emptyList();
        }

        if(type instanceof DeclaredType) {

            DeclaredType declaredType = (DeclaredType) type;
            return declaredType.getTypeArguments().stream()
                    .map(typeArg -> {
                        TypeKind kind = typeArg.getKind();
                        if (kind == WILDCARD) {
                            WildcardType wcType = (WildcardType)typeArg;
                            TypeMirror extendsBound = wcType.getExtendsBound();
                            TypeMirror superBound = wcType.getSuperBound();
                            if(extendsBound == null && superBound == null) {
                                return Object.class.getName();
                            }
                            else if(extendsBound != null) {
                                return typeUtils.erasure(extendsBound).toString();
                            }
                            else if(superBound != null) {
                                return typeUtils.erasure(superBound).toString();
                            }
                            else {
                                return typeUtils.getWildcardType(extendsBound, superBound).toString();
                            }
                        }
                        return typeArg.toString();
                    })
                    .collect(Collectors.toList());
        }
        else if(type instanceof TypeVariable) {
            TypeVariable var = (TypeVariable) type;
            TypeMirror upperBound = var.getUpperBound();
            if(upperBound instanceof DeclaredType) {
                return resolveGenericTypes(upperBound);
            }
        }
        return Collections.emptyList();
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
