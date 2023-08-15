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
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

    private static final int MAX_ITEMS_IN_CACHE = 200;
    private final Map<CacheKey, Element> elementsCache = new LinkedHashMap<>();

    /**
     * Get native class element.
     *
     * @param classElement The class element
     * @return The native element
     */
    protected C getNativeClassType(ClassElement classElement) {
        return (C) classElement.getNativeType();
    }

    /**
     * Get native element.
     *
     * @param element The element
     * @return The native element
     */
    protected N getNativeType(Element element) {
        return (N) element.getNativeType();
    }

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
        Predicate<T> filter = element -> {
            if (excludeElements.contains(getNativeType(element))) {
                return false;
            }
            List<Predicate<T>> elementPredicates = result.getElementPredicates();
            if (!elementPredicates.isEmpty()) {
                for (Predicate<T> elementPredicate : elementPredicates) {
                    if (!elementPredicate.test(element)) {
                        return false;
                    }
                }
            }
            if (element instanceof MethodElement methodElement) {
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
                    } else if (element instanceof MethodElement methodElement) {
                        ce = methodElement.getGenericReturnType();
                    } else if (element instanceof ClassElement theClass) {
                        ce = theClass;
                    } else if (element instanceof FieldElement fieldElement) {
                        ce = fieldElement.getGenericField();
                    } else {
                        throw new IllegalStateException("Unknown element: " + element);
                    }
                    if (!typePredicate.test(ce)) {
                        return false;
                    }
                }
            }
            return true;
        };
        Collection<T> allElements = getAllElements(getNativeClassType(classElement), result.isOnlyDeclared(), (t1, t2) -> reduceElements(t1, t2, result), result);
        return allElements
                .stream()
                .filter(filter)
                .toList();
    }

    private boolean reduceElements(io.micronaut.inject.ast.Element newElement,
                                   io.micronaut.inject.ast.Element existingElement,
                                   ElementQuery.Result<?> result) {
        if (!result.isIncludeHiddenElements()) {
            if (newElement instanceof FieldElement newFiledElement && existingElement instanceof FieldElement existingFieldElement) {
                return newFiledElement.hides(existingFieldElement);
            }
            if (newElement instanceof MethodElement newMethodElement && existingElement instanceof MethodElement existingMethodElement) {
                if (newMethodElement.hides(existingMethodElement)) {
                    return true;
                }
            }
        }
        if (!result.isIncludeOverriddenMethods()) {
            if (newElement instanceof MethodElement newMethodElement && existingElement instanceof MethodElement existingMethodElement) {
                return newMethodElement.overrides(existingMethodElement);
            } else if (newElement instanceof PropertyElement newPropertyElement && existingElement instanceof PropertyElement existingPropertyElement) {
                return newPropertyElement.overrides(existingPropertyElement);
            }
        }
        return false;
    }

    private <T extends io.micronaut.inject.ast.Element> Collection<T> getAllElements(C classNode,
                                                                                     boolean onlyDeclared,
                                                                                     BiPredicate<T, T> reduce,
                                                                                     ElementQuery.Result<?> result) {
        Set<T> elements = new LinkedHashSet<>();
        List<List<N>> hierarchy = new ArrayList<>();
        collectHierarchy(classNode, onlyDeclared, hierarchy, result);
        for (List<N> classElements : hierarchy) {
            Set<T> addedFromClassElements = new LinkedHashSet<>();
            classElements:
            for (N element : classElements) {
                List<Predicate<String>> namePredicates = result.getNamePredicates();
                if (!namePredicates.isEmpty()) {
                    String elementName = getElementName(element);
                    for (Predicate<String> namePredicate : namePredicates) {
                        if (!namePredicate.test(elementName)) {
                            continue classElements;
                        }
                    }
                }

                N nativeType = getCacheKey(element);
                CacheKey cacheKey = new CacheKey(result.getElementType(), nativeType);
                T newElement = (T) elementsCache.computeIfAbsent(cacheKey, ck -> toAstElement(nativeType, result.getElementType()));
                if (result.getElementType() == MemberElement.class) {
                    // Also cache members query results as it's original element type
                    if (newElement instanceof FieldElement) {
                        elementsCache.putIfAbsent(new CacheKey(FieldElement.class, nativeType), newElement);
                    } else if (newElement instanceof ConstructorElement) {
                        elementsCache.putIfAbsent(new CacheKey(ConstructorElement.class, nativeType), newElement);
                        elementsCache.putIfAbsent(new CacheKey(MethodElement.class, nativeType), newElement);
                    } else if (newElement instanceof MethodElement) {
                        elementsCache.putIfAbsent(new CacheKey(MethodElement.class, nativeType), newElement);
                    } else if (newElement instanceof PropertyElement) {
                        elementsCache.putIfAbsent(new CacheKey(PropertyElement.class, nativeType), newElement);
                    }
                } else if (MemberElement.class.isAssignableFrom(result.getElementType())) {
                    elementsCache.putIfAbsent(new CacheKey(MemberElement.class, nativeType), newElement);
                }
                if (elementsCache.size() == MAX_ITEMS_IN_CACHE) {
                    Iterator<Map.Entry<CacheKey, Element>> iterator = elementsCache.entrySet().iterator();
                    iterator.next();
                    iterator.remove();
                }
                if (!result.getElementType().isInstance(newElement)) {
                    // dirty cache
                    elementsCache.remove(cacheKey);
                    newElement = (T) elementsCache.computeIfAbsent(cacheKey, ck -> toAstElement(nativeType, result.getElementType()));
                }
                for (Iterator<T> iterator = elements.iterator(); iterator.hasNext(); ) {
                    T existingElement = iterator.next();
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


    /**
     * Gets the element name.
     * @param element The element
     * @return The name
     */
    protected abstract String getElementName(N element);

    /**
     * Get the cache key.
     *
     * @param element The element
     * @return The cache key
     */
    protected N getCacheKey(N element) {
        return element;
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
     * @param nativeType The native element.
     * @param elementType     The result type
     * @return The AST element
     */
    @NonNull
    protected abstract io.micronaut.inject.ast.Element toAstElement(N nativeType, Class<?> elementType);

    private record CacheKey(Class<?> elementType, Object nativeType) {
    }
}
