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

import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Provides utility method for working with the annotation processor AST.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class ModelUtils {

    private final Elements elementUtils;
    private final Types typeUtils;

    /**
     * @param elementUtils The {@link Elements}
     * @param typeUtils The {@link Types}
     */
    protected ModelUtils(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    /**
     * @return The type utilities
     */
    public Types getTypeUtils() {
        return typeUtils;
    }

    /**
     * Resolves type elements from the provided annotated elements.
     *
     * @param annotatedElements The elements to process
     * @return the type elements
     */
    public Stream<TypeElement> resolveTypeElements(Set<? extends Element> annotatedElements) {
        return annotatedElements
            .stream()
            .flatMap(element -> {
                if (element instanceof ExecutableElement executableElement) {
                    return Stream.of(executableElement.getEnclosingElement());
                }
                if (element instanceof VariableElement variableElement) {
                    return Stream.of(variableElement.getEnclosingElement());
                }
                if (element instanceof PackageElement packageElement) {
                    return packageElement.getEnclosedElements().stream();
                }
                return Stream.of(element);
            })
            .filter(element -> JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isEnum(element) || JavaModelUtils.isRecord(element))
            .map(this::classElementFor)
            .filter(Objects::nonNull)
            .filter(element -> element.getAnnotation(Generated.class) == null);
    }

    /**
     * Obtains the {@link TypeElement} for a given element.
     *
     * @param element The element
     * @return The {@link TypeElement}
     */
    @Nullable
    public final TypeElement classElementFor(Element element) {
        while (element != null && !(JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isRecord(element) || JavaModelUtils.isEnum(element))) {
            element = element.getEnclosingElement();
        }
        if (element instanceof TypeElement e) {
            return e;
        }
        return null;
    }

    /**
     * Resolves a type name for the given name.
     *
     * @param type The type
     * @return The type reference
     */
    String resolveTypeName(TypeMirror type) {
        TypeMirror typeMirror = resolveTypeReference(type);
        if (typeMirror.getKind().isPrimitive()) {
            return typeMirror.toString();
        }

        TypeElement typeElement = (TypeElement) typeUtils.asElement(typeMirror);
        if (typeElement != null) {
            return elementUtils.getBinaryName(typeElement).toString();
        } else {
            return typeUtils.erasure(typeMirror).toString();
        }
    }

    /**
     * Resolves a type reference for the given type mirror. A type reference is either a reference to the concrete
     * {@link Class} or a String representing the type name.
     *
     * @param type The type
     * @return The type reference
     */
    TypeMirror resolveTypeReference(TypeMirror type) {
        TypeKind typeKind = type.getKind();
        if (typeKind.isPrimitive()) {
            return type;
        } else if (typeKind == TypeKind.DECLARED) {
            DeclaredType dt = (DeclaredType) type;
            if (dt.getTypeArguments().isEmpty()) {
                return dt;
            }
            return typeUtils.erasure(type);
        } else {
            return typeUtils.erasure(type);
        }
    }

    /**
     * Return whether the element is abstract.
     *
     * @param element The element
     * @return True if it is abstract
     */
    boolean isAbstract(Element element) {
        return element.getModifiers().contains(ABSTRACT);
    }

    /**
     * Return whether the element is static.
     *
     * @param element The element
     * @return True if it is static
     */
    boolean isStatic(Element element) {
        return element.getModifiers().contains(STATIC);
    }

    /**
     * The Java APT throws an internal exception {code com.sun.tools.javac.code.Symbol$CompletionFailure} if a class is missing from the classpath and {@link Element#getKind()} is called. This method
     * handles exceptions when calling the getKind() method to avoid this scenario and should be used instead of {@link Element#getKind()}.
     *
     * @param element The element
     * @param expected The expected kind
     * @return The kind, if it is resolvable and matches the expected kind
     */
    public Optional<ElementKind> resolveKind(Element element, ElementKind expected) {
        final Optional<ElementKind> elementKind = resolveKind(element);
        if (elementKind.isPresent() && elementKind.get() == expected) {
            return elementKind;
        }
        return Optional.empty();
    }

    /**
     * The Java APT throws an internal exception {code com.sun.tools.javac.code.Symbol$CompletionFailure} if a class is missing from the classpath and {@link Element#getKind()} is called. This method
     * handles exceptions when calling the getKind() method to avoid this scenario and should be used instead of {@link Element#getKind()}.
     *
     * @param element The element
     * @return The kind, if it is resolvable
     */
    public Optional<ElementKind> resolveKind(Element element) {
        if (element != null) {

            try {
                final ElementKind kind = element.getKind();
                return Optional.of(kind);
            } catch (Exception e) {
                // ignore and fall through to empty
            }
        }
        return Optional.empty();
    }
}
