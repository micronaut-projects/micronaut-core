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
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
    private static final int MAX_RESULTS = 20;
    private final Map<CacheKey, Element> elementsCache = new LinkedHashMap<>();
    private final Map<QueryResultKey, List<?>> resultsCache = new LinkedHashMap<>();

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

        QueryResultKey queryResultKey = new QueryResultKey(result, classElement.getNativeType());
        List<T> values = (List<T>) resultsCache.get(queryResultKey);
        if (values != null) {
            return values;
        }

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
                if (element instanceof MemberElement memberElement && !memberElement.isAccessible()) {
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
                ClassElement ce = memberType(classElement, element);
                for (Predicate<ClassElement> typePredicate : result.getTypePredicates()) {
                    if (!typePredicate.test(ce)) {
                        return false;
                    }
                }
            }
            return true;
        };

        C nativeClassType = getNativeClassType(classElement);
        List<T> elements;
        if (result.isOnlyDeclared() || classElement.getSuperType().isEmpty() && classElement.getInterfaces().isEmpty()) {
            elements = getElements(nativeClassType, result, filter);
        } else {
            ElementQuery.Result<T> resultWithoutPredicates = result.withoutPredicates();
            QueryResultKey queryWithoutPredicatesResultKey = new QueryResultKey(resultWithoutPredicates, classElement.getNativeType());
            if (queryWithoutPredicatesResultKey.equals(queryResultKey)) {
                // No predicates query
                elements = getAllElements(nativeClassType, (t1, t2) -> reduceElements(t1, t2, result), result);
            } else {
                // Let's try to load the result without predicates and cache it, then apply predicates
                List<T> valuesWithoutPredicates = (List<T>) resultsCache.get(queryWithoutPredicatesResultKey);
                if (valuesWithoutPredicates != null) {
                    return valuesWithoutPredicates.stream().filter(filter).toList();
                }
                elements = getAllElements(nativeClassType, (t1, t2) -> reduceElements(t1, t2, resultWithoutPredicates), resultWithoutPredicates);
                // This collection is before predicates are applied, we can store it and reuse
                resultsCache.put(queryWithoutPredicatesResultKey, new ArrayList<>(elements));
                // The second filtered result
                elements.removeIf(element -> !filter.test(element));
            }
        }
        resultsCache.put(queryResultKey, elements);
        adjustMapCapacity(resultsCache, MAX_RESULTS);
        return elements;
    }

    private <T extends Element> ClassElement memberType(ClassElement classElement, T element) {
        if (element instanceof ConstructorElement) {
            return classElement;
        } else if (element instanceof MethodElement methodElement) {
            return methodElement.getGenericReturnType();
        } else if (element instanceof ClassElement theClass) {
            return theClass;
        } else if (element instanceof FieldElement fieldElement) {
            return fieldElement.getGenericField();
        } else {
            throw new IllegalStateException("Unknown element: " + element);
        }
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

    private <T extends io.micronaut.inject.ast.Element> List<T> getAllElements(C classNode,
                                                                               BiPredicate<T, T> reduce,
                                                                               ElementQuery.Result<?> result) {

        List<T> elements = new LinkedList<>();
        List<T> addedFromClassElements = new ArrayList<>(20);
        if (isInterface(classNode)) {
            processInterfaceHierarchy(classNode, reduce, result, addedFromClassElements, elements, true);
        } else {
            boolean includeAbstract = isAbstractClass(classNode) || result.isIncludeOverriddenMethods();
            processClassHierarchy(classNode, reduce, result, addedFromClassElements, elements, includeAbstract);
        }
        return elements;
    }

    private <T extends Element> void processClassHierarchy(C classNode,
                                                           BiPredicate<T, T> reduce,
                                                           ElementQuery.Result<?> result,
                                                           List<T> addedFromClassElements,
                                                           List<T> collectedElements,
                                                           boolean includeAbstract) {
        if (excludeClass(classNode)) {
            return;
        }
        C superClass = getSuperClass(classNode);
        if (superClass != null) {
            processClassHierarchy(superClass, reduce, result, addedFromClassElements, collectedElements, includeAbstract);
        }
        reduce(collectedElements, getEnclosedElements(classNode, result, includeAbstract), reduce, result, addedFromClassElements, false, false);
        for (C anInterface : getInterfaces(classNode)) {
            processInterfaceHierarchy(anInterface, reduce, result, addedFromClassElements, collectedElements, includeAbstract);
        }
    }

    private <T extends Element> void processInterfaceHierarchy(C classNode,
                                                               BiPredicate<T, T> reduce,
                                                               ElementQuery.Result<?> result,
                                                               List<T> addedFromClassElements,
                                                               Collection<T> collectedElements,
                                                               boolean includeAbstract) {
        if (excludeClass(classNode)) {
            return;
        }
        for (C anInterface : getInterfaces(classNode)) {
            processInterfaceHierarchy(anInterface, reduce, result, addedFromClassElements, collectedElements, includeAbstract);
        }
        reduce(collectedElements, getEnclosedElements(classNode, result, includeAbstract), reduce, result, addedFromClassElements, true, includeAbstract);
    }

    protected abstract boolean hasAnnotation(N element, Class<? extends Annotation> annotation);

    private <T extends Element> void reduce(Collection<T> collectedElements,
                                            List<N> classElements,
                                            BiPredicate<T, T> reduce,
                                            ElementQuery.Result<?> result,
                                            List<T> addedFromClassElements,
                                            boolean isInterface,
                                            boolean includesAbstract) {
        List<Predicate<String>> namePredicates = result.getNamePredicates();
        boolean hasNamePredicates = !namePredicates.isEmpty();
        addedFromClassElements.clear(); // Reusing this collection for all the calls
        classElements:
        for (N element : classElements) {
            if (hasNamePredicates) {
                String elementName = getElementName(element);
                for (Predicate<String> namePredicate : namePredicates) {
                    if (!namePredicate.test(elementName)) {
                        continue classElements;
                    }
                }
            }
            if (hasAnnotation(element, Vetoed.class)) {
                continue;
            }
            T newElement = convertElement(result, element);
            String newElementName = newElement.getName();
            for (Iterator<T> iterator = collectedElements.iterator(); iterator.hasNext(); ) {
                T existingElement = iterator.next();
                if (!existingElement.getName().equals(newElementName)) {
                    continue;
                }
                if (isInterface) {
                    if (existingElement == newElement) {
                        continue classElements;
                    }
                    if (!newElement.isAbstract()) {
                        if (reduce.test(newElement, existingElement)) {
                            iterator.remove();
                            addedFromClassElements.add(newElement);
                            continue classElements;
                        }
                        if (!existingElement.isAbstract() && reduce.test(existingElement, newElement)) {
                            continue classElements;
                        }
                    } else if (reduce.test(existingElement, newElement)) {
                        continue classElements;
                    } else if (includesAbstract && reduce.test(newElement, existingElement)) {
                        iterator.remove();
                        addedFromClassElements.add(newElement);
                        continue classElements;
                    }
                } else if (reduce.test(newElement, existingElement)) {
                    iterator.remove();
                    addedFromClassElements.add(newElement);
                    continue classElements;
                }

            }
            addedFromClassElements.add(newElement);
        }
        collectedElements.addAll(addedFromClassElements);
    }

    private <T extends io.micronaut.inject.ast.Element> List<T> getElements(C classNode,
                                                                            ElementQuery.Result<?> result,
                                                                            Predicate<T> filter) {
        List<N> enclosedElements = getEnclosedElements(classNode, result, true);
        List<T> elements = new ArrayList<>(enclosedElements.size());
        List<Predicate<String>> namePredicates = result.getNamePredicates();
        boolean hasNamePredicates = !namePredicates.isEmpty();
        enclosedElementsLoop:
        for (N enclosedElement : enclosedElements) {
            if (hasNamePredicates) {
                String elementName = getElementName(enclosedElement);
                for (Predicate<String> namePredicate : namePredicates) {
                    if (!namePredicate.test(elementName)) {
                        continue enclosedElementsLoop;
                    }
                }
            }
            if (hasAnnotation(enclosedElement, Vetoed.class)) {
                continue;
            }
            T element = convertElement(result, enclosedElement);
            if (filter.test(element)) {
                elements.add(element);
            }
        }
        return elements;
    }

    private <T extends Element> T convertElement(ElementQuery.Result<?> result, N element) {
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
        adjustMapCapacity(elementsCache, MAX_ITEMS_IN_CACHE);
        if (!result.getElementType().isInstance(newElement)) {
            // dirty cache
            elementsCache.remove(cacheKey);
            newElement = (T) elementsCache.computeIfAbsent(cacheKey, ck -> toAstElement(nativeType, result.getElementType()));
        }
        return newElement;
    }

    private void adjustMapCapacity(Map<?, ?> map, int size) {
        if (map.size() == size) {
            Iterator<?> iterator = map.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * Gets the element name.
     *
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
     * @param classNode       The class
     * @param result          The query result
     * @param includeAbstract If abstract/non-default elements should be included
     * @return The enclosed elements
     */
    @NonNull
    protected abstract List<N> getEnclosedElements(C classNode, ElementQuery.Result<?> result, boolean includeAbstract);

    /**
     * Extracts the enclosed elements of the class.
     *
     * @param classNode The class
     * @param result    The query result
     * @return The enclosed elements
     */
    @NonNull
    protected List<N> getEnclosedElements(C classNode, ElementQuery.Result<?> result) {
        return getEnclosedElements(classNode, result, true);
    }

    /**
     * Checks if the class needs to be excluded.
     *
     * @param classNode The class
     * @return true if to exclude
     */
    protected abstract boolean excludeClass(C classNode);

    /**
     * Is abstract class.
     *
     * @param classNode The class node
     * @return true if abstract
     * @since 4.3.0
     */
    protected abstract boolean isAbstractClass(C classNode);

    /**
     * Is interface.
     *
     * @param classNode The class node
     * @return true if interface
     * @since 4.3.0
     */
    protected abstract boolean isInterface(C classNode);

    /**
     * Converts the native element to the AST element.
     *
     * @param nativeType  The native element.
     * @param elementType The result type
     * @return The AST element
     */
    @NonNull
    protected abstract io.micronaut.inject.ast.Element toAstElement(N nativeType, Class<?> elementType);

    private record CacheKey(Class<?> elementType, Object nativeType) {
    }

    private record QueryResultKey(ElementQuery.Result<?> result, Object nativeType) {
    }
}
