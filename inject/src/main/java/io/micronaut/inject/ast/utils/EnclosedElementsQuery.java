/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.ast.utils;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * The elements query helper.
 *
 * @param <C> The class native element type
 * @param <N> The native element type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class EnclosedElementsQuery<C, N> {

    private final Map<N, io.micronaut.inject.ast.Element> elementsCache = new HashMap<>();

    /**
     * Return the elements that match the given query.
     *
     * @param classElement The class element
     * @param query        The query to use.
     * @param <T>          The element type
     * @return The fields
     */
    public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(ClassElement classElement, @NonNull ElementQuery<T> query) {
        Objects.requireNonNull(query, "Query cannot be null");
        ElementQuery.Result<T> result = query.result();
        Set<N> excludeElements = getExcludedNativeElements(result);
        Predicate<io.micronaut.inject.ast.Element> filter = element -> {
            if (excludeElements.contains(element.getNativeType())) {
                return false;
            }
            List<Predicate<T>> elementPredicates = result.getElementPredicates();
            if (!elementPredicates.isEmpty()) {
                for (Predicate<T> elementPredicate : elementPredicates) {
                    if (!elementPredicate.test((T) element)) {
                        return false;
                    }
                }
            }
            if (element instanceof MethodElement) {
                MethodElement methodElement = (MethodElement) element;
                if (result.isOnlyAbstract()) {
                    if (methodElement.getDeclaringType().isInterface() && methodElement.isDefault()) {
                        return false;
                    } else if (!element.isAbstract()) {
                        return false;
                    }
                } else if (result.isOnlyConcrete()) {
                    if (methodElement.getDeclaringType().isInterface() && !methodElement.isDefault()) {
                        return false;
                    } else if (element.isAbstract()) {
                        return false;
                    }
                }
            }
            if (result.isOnlyInstance() && element.isStatic()) {
                return false;
            } else if (result.isOnlyStatic() && !element.isStatic()) {
                return false;
            }
            if (result.isOnlyAccessible()) {
                // exclude private members
                // exclude synthetic members or bridge methods that start with $
                if (element.isPrivate() || element.getName().startsWith("$")) {
                    return false;
                }
                if (element instanceof MemberElement && !((MemberElement) element).isAccessible()) {
                    return false;
                }
            }
            if (!result.getModifierPredicates().isEmpty()) {
                Set<ElementModifier> modifiers = element.getModifiers();
                for (Predicate<Set<ElementModifier>> modifierPredicate : result.getModifierPredicates()) {
                    if (!modifierPredicate.test(modifiers)) {
                        return false;
                    }
                }
            }
            if (!result.getNamePredicates().isEmpty()) {
                for (Predicate<String> namePredicate : result.getNamePredicates()) {
                    if (!namePredicate.test(element.getName())) {
                        return false;
                    }
                }
            }
            if (!result.getAnnotationPredicates().isEmpty()) {
                for (Predicate<AnnotationMetadata> annotationPredicate : result.getAnnotationPredicates()) {
                    if (!annotationPredicate.test(element)) {
                        return false;
                    }
                }
            }
            if (!result.getTypePredicates().isEmpty()) {
                for (Predicate<ClassElement> typePredicate : result.getTypePredicates()) {
                    ClassElement ce;
                    if (element instanceof ConstructorElement) {
                        ce = classElement;
                    } else if (element instanceof MethodElement) {
                        ce = ((MethodElement) element).getGenericReturnType();
                    } else if (element instanceof ClassElement) {
                        ce = (ClassElement) element;
                    } else if (element instanceof FieldElement) {
                        ce = ((FieldElement) element).getGenericField();
                    } else {
                        throw new IllegalStateException("Unknown element: " + element);
                    }
                    if (!typePredicate.test(ce)) {
                        return false;
                    }
                }
            }
// TODO: FIX only injected
//                if (result.isOnlyInjected() && !element.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {
//                    return false;
//                }
            return true;
        };
        return (List<T>) getAllElements((C) classElement.getNativeType(), result.isOnlyDeclared(), (t1, t2) -> reduceElements(t1, t2, result), result)
            .stream()
            .filter(filter)
            .toList();
    }

    private boolean reduceElements(io.micronaut.inject.ast.Element newElement,
                                   io.micronaut.inject.ast.Element existingElement,
                                   ElementQuery.Result<?> result) {
        if (!result.isIncludeHiddenElements()) {
            if (newElement instanceof FieldElement && existingElement instanceof FieldElement) {
                return ((FieldElement) newElement).hides((FieldElement) existingElement);
            }
            if (newElement instanceof MethodElement && existingElement instanceof MethodElement) {
                if (((MethodElement) newElement).hides((MethodElement) existingElement)) {
                    return true;
                }
            }
        }
        if (!result.isIncludeOverriddenMethods()) {
            if (newElement instanceof MethodElement && existingElement instanceof MethodElement) {
                return ((MethodElement) newElement).overrides((MethodElement) existingElement);
            }
        }
        return false;
    }

    private Collection<io.micronaut.inject.ast.Element> getAllElements(C classNode,
                                                                       boolean onlyDeclared,
                                                                       BiPredicate<io.micronaut.inject.ast.Element, io.micronaut.inject.ast.Element> reduce,
                                                                       ElementQuery.Result<?> result) {
        Set<io.micronaut.inject.ast.Element> elements = new LinkedHashSet<>();
        List<List<N>> hierarchy = new ArrayList<>();
        collectHierarchy(classNode, onlyDeclared, hierarchy, result);
        for (List<N> classElements : hierarchy) {
            Set<io.micronaut.inject.ast.Element> addedFromClassElements = new LinkedHashSet<>();
            classElements:
            for (N element : classElements) {
                io.micronaut.inject.ast.Element newElement = elementsCache.computeIfAbsent(element, this::toAstElement);
                for (Iterator<io.micronaut.inject.ast.Element> iterator = elements.iterator(); iterator.hasNext(); ) {
                    io.micronaut.inject.ast.Element existingElement = iterator.next();
                    if (newElement.equals(existingElement)) {
                        continue;
                    }
                    if (reduce.test(newElement, existingElement)) {
                        iterator.remove();
                        addedFromClassElements.add(newElement);
                    } else if (reduce.test(existingElement, newElement)) {
                        continue classElements;
                    }
                }
                addedFromClassElements.add(newElement);
            }
            elements.addAll(addedFromClassElements);
        }
        return elements;
    }

    private void collectHierarchy(C classNode,
                                  boolean onlyDeclared,
                                  List<List<N>> hierarchy,
                                  ElementQuery.Result<?> result) {
        if (excludeClass(classNode)) {
            return;
        }
        if (!onlyDeclared) {
            C superclass = getSuperClass(classNode);
            if (superclass != null) {
                collectHierarchy(superclass, false, hierarchy, result);
            }
            for (C interfaceNode : getInterfaces(classNode)) {
                List<List<N>> interfaceElements = new ArrayList<>();
                collectHierarchy(interfaceNode, false, interfaceElements, result);
                hierarchy.addAll(interfaceElements);
            }
        }
        hierarchy.add(getEnclosedElements(classNode, result));
    }

    /**
     * Provides a collection of the native elements to exclude.
     *
     * @param result The result
     * @return the collection of excluded elements
     */
    protected Set<N> getExcludedNativeElements(@NonNull ElementQuery.Result<?> result) {
        return Collections.emptySet();
    }

    /**
     * Extracts the super class.
     *
     * @param classNode The class
     * @return The super calss
     */
    @Nullable
    protected abstract C getSuperClass(C classNode);

    /**
     * Extracts the interfaces of the class.
     *
     * @param classNode The class
     * @return The interfaces
     */
    @NonNull
    protected abstract Collection<C> getInterfaces(C classNode);

    /**
     * Extracts the enclosed elements of the class.
     *
     * @param classNode The class
     * @param result    The query result
     * @return The enclosed elements
     */
    @NonNull
    protected abstract List<N> getEnclosedElements(C classNode, ElementQuery.Result<?> result);

    /**
     * Checks if the class needs to be excluded.
     *
     * @param classNode The class
     * @return true if to exclude
     */
    protected abstract boolean excludeClass(C classNode);

    /**
     * Converts the native element to the AST element.
     *
     * @param enclosedElement The native element.
     * @return The AST element
     */
    @NonNull
    protected abstract io.micronaut.inject.ast.Element toAstElement(N enclosedElement);

}
