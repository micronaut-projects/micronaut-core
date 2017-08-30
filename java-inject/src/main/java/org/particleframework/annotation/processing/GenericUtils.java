package org.particleframework.annotation.processing;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static javax.lang.model.type.TypeKind.VOID;

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

    // FIXME I don't know if this works correctly.resolveGenericTypes
    // Needs a test case. See InjectTransform.resolveGenericTypes
    // It doesn't get covered either.
    List<Object> resolveGenericTypes(TypeMirror type) {
        if (type.getKind().isPrimitive() || type.getKind() == VOID) {
            return Collections.emptyList();
        }
        List<Object> generics = new ArrayList<>();
        ((DeclaredType)type).getTypeArguments().forEach(tm -> {
            TypeElement element = (TypeElement) typeUtils.asElement(tm);
            generics.add(element.getQualifiedName());
        });
        return generics;
    }
}
