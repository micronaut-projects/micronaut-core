/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.utils;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The native elements helper.
 *
 * @param <C> The class native element type
 * @param <M> The method native element type
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Internal
public abstract class NativeElementsHelper<C, M> {

    private final Set<Object> processedClasses = new HashSet<>();
    private final Map<MethodCacheKey, Collection<M>> overridesCache = new HashMap<>();

    /**
     * @param nativeClassType The native class type to cleanup
     */
    public void cleanupForClass(Object nativeClassType) {
        processedClasses.remove(nativeClassType);
        overridesCache.entrySet().removeIf(e -> e.getKey().classKey.equals(nativeClassType));
    }

    /**
     * Check if one method overrides another.
     *
     * @param m1    The override method
     * @param m2    The overridden method
     * @param owner The class owner of the methods
     * @return true if overridden
     */
    protected abstract boolean overrides(M m1, M m2, C owner);

    /**
     * Gets the element name.
     *
     * @param element The element
     * @return The name
     */
    @NonNull
    protected abstract String getMethodName(M element);

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
     * @return The enclosed elements
     */
    @NonNull
    protected abstract List<M> getMethods(C classNode);

    /**
     * Checks if the class needs to be excluded.
     *
     * @param classNode The class
     * @return true if to exclude
     */
    protected abstract boolean excludeClass(C classNode);

    /**
     * Is interface.
     *
     * @param classNode The class node
     * @return true if interface
     */
    protected abstract boolean isInterface(C classNode);

    /**
     * Get a class cache key.
     *
     * @param classElement The class element
     * @return a new key or the previous value
     */
    protected Object getClassCacheKey(C classElement) {
        return classElement;
    }

    /**
     * Get a method cache key.
     *
     * @param methodElement The method element
     * @return a new key or the previous value
     */
    protected Object getMethodCacheKey(M methodElement) {
        return methodElement;
    }

    /**
     * Populate with the class hierarchy.
     *
     * @param element   The element
     * @param hierarchy The hierarchy
     */
    public final void populateTypeHierarchy(C element, List<C> hierarchy) {
        for (C anInterface : getInterfaces(element)) {
            populateTypeHierarchy(anInterface, hierarchy);
        }
        C superClass = getSuperClass(element);
        if (superClass != null) {
            populateTypeHierarchy(superClass, hierarchy);
        }
        if (!excludeClass(element)) {
            hierarchy.add(element);
        }
    }

    /**
     * Find overridden methods.
     *
     * @param classNode     The class of the method
     * @param methodElement The method
     * @return the overridden methods
     */
    public final Collection<M> findOverriddenMethods(C classNode, M methodElement) {
        Object classCacheKey = getClassCacheKey(classNode);
        MethodCacheKey methodCacheKey = new MethodCacheKey(
            classCacheKey,
            getMethodCacheKey(methodElement)
        );
        Collection<M> overriddenMethods = overridesCache.get(methodCacheKey);
        if (overriddenMethods != null) {
            return overriddenMethods;
        }
        if (processedClasses.contains(classCacheKey)) {
            return List.of();
        }
        List<MethodElement<M>> allElements = getAllElements(classNode);
        for (MethodElement<M> method : allElements) {
            if (method.overridden.isEmpty()) {
                continue;
            }
            overridesCache.put(
                new MethodCacheKey(
                    classCacheKey,
                    getMethodCacheKey(method.methodElement)
                ),
                method.overridden
            );
        }
        processedClasses.add(classCacheKey);
        return overridesCache.getOrDefault(methodCacheKey, List.of());
    }

    private List<MethodElement<M>> getAllElements(C classNode) {
        List<MethodElement<M>> elements = new LinkedList<>();
        List<MethodElement<M>> cache = new ArrayList<>(20);
        if (isInterface(classNode)) {
            processInterfaceHierarchy(classNode, classNode, cache, elements, true);
        } else {
            processClassHierarchy(classNode, classNode, cache, elements, true);
        }
        return elements;
    }

    private void processClassHierarchy(C owner,
                                       C classNode,
                                       List<MethodElement<M>> cache,
                                       List<MethodElement<M>> collectedMethods,
                                       boolean includeAbstract) {
        if (excludeClass(classNode)) {
            return;
        }
        C superClass = getSuperClass(classNode);
        if (superClass != null) {
            processClassHierarchy(owner, superClass, cache, collectedMethods, includeAbstract);
        }
        reduce(owner, collectedMethods, getMethods(classNode), cache, false, false);
        for (C anInterface : getInterfaces(classNode)) {
            processInterfaceHierarchy(owner, anInterface, cache, collectedMethods, includeAbstract);
        }
    }

    private void processInterfaceHierarchy(C owner,
                                           C classNode,
                                           List<MethodElement<M>> cache,
                                           Collection<MethodElement<M>> collectedMethods,
                                           boolean includeAbstract) {
        if (excludeClass(classNode)) {
            return;
        }
        for (C anInterface : getInterfaces(classNode)) {
            processInterfaceHierarchy(owner, anInterface, cache, collectedMethods, includeAbstract);
        }
        reduce(owner, collectedMethods, getMethods(classNode), cache, true, includeAbstract);
    }

    private void reduce(C owner,
                        Collection<MethodElement<M>> collectedMethods,
                        List<M> newMethodElements,
                        List<MethodElement<M>> cache,
                        boolean isInterface,
                        boolean includesAbstract) {
        cache.clear(); // Reusing this collection for all the calls
        classElements:
        for (M newElement : newMethodElements) {

            for (Iterator<MethodElement<M>> iterator = collectedMethods.iterator(); iterator.hasNext(); ) {
                MethodElement<M> existingEntry = iterator.next();
                M existingElement = existingEntry.methodElement;
                if (!getMethodName(existingElement).equals(getMethodName(newElement))) {
                    continue;
                }
                LinkedHashSet<M> overridden = existingEntry.overridden;
                if (isInterface) {
                    if (existingElement == newElement) {
                        continue classElements;
                    }
                    if (overrides(existingElement, newElement, owner)) {
                        overridden.add(newElement);
                        continue classElements;
                    } else if (includesAbstract && overrides(newElement, existingElement, owner)) {
                        iterator.remove();
                        overridden.add(existingElement);
                        cache.add(new MethodElement<>(newElement, overridden));
                        continue classElements;
                    }
                } else if (overrides(newElement, existingElement, owner)) {
                    iterator.remove();
                    overridden.add(existingElement);
                    cache.add(new MethodElement<>(newElement, overridden));
                    continue classElements;
                }

            }
            cache.add(new MethodElement<>(newElement, new LinkedHashSet<>()));
        }
        collectedMethods.addAll(cache);
    }

    /**
     * The method element.
     *
     * @param methodElement The element
     * @param overridden    The overridden collection
     * @param <N>           The native method element type
     */
    public record MethodElement<N>(N methodElement, LinkedHashSet<N> overridden) {
    }

    private record MethodCacheKey(Object classKey, Object methodKey) {
    }

}
