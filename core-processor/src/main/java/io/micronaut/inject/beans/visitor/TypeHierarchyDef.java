/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.beans.GeneratedBeanTypeHierarchy;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The expressions of the arguments of the {@link GeneratedBeanTypeHierarchy} an introspection generates. Every
 * type is written once and referenced by its index: depth first, the super class before the interfaces. Each
 * bean method lists its declaring levels by those indexes: the type declaring it, then the types declaring a
 * method it overrides.
 *
 * @param types The types, each once
 * @param superTypes For each type, the indexes of the types it extends and implements
 * @param methodLevels For each bean method, the indexes of its declaring levels
 * @author Denis Stepanov
 * @since 5.3.0
 */
record TypeHierarchyDef(ExpressionDef types, ExpressionDef superTypes, ExpressionDef methodLevels) {

    /**
     * Builds the expressions describing the hierarchy of a bean type.
     *
     * @param beanClassElement The bean type
     * @param beanMethods The bean methods of the introspection, in their order
     * @param loadClassValueExpressionFn The load type expression fn
     * @return The expressions
     */
    static TypeHierarchyDef of(ClassElement beanClassElement, List<MethodElement> beanMethods,
                               Function<String, ExpressionDef> loadClassValueExpressionFn) {
        Map<String, ClassElement> hierarchy = new LinkedHashMap<>();
        collectHierarchy(beanClassElement, hierarchy);
        Map<String, Integer> table = new LinkedHashMap<>();
        hierarchy.keySet().forEach(name -> table.put(name, table.size()));

        List<ExpressionDef> superTypes = new ArrayList<>(hierarchy.size());
        for (ClassElement type : hierarchy.values()) {
            List<ExpressionDef> parents = new ArrayList<>();
            if (type.isInterface()) {
                parents.add(ExpressionDef.constant(GeneratedBeanTypeHierarchy.NO_SUPERCLASS));
            } else {
                // a class extending Object directly reports no super type
                parents.add(ExpressionDef.constant(type.getSuperType()
                    .filter(parent -> !parent.getName().equals(Object.class.getName()))
                    .map(parent -> table.get(parent.getName()))
                    .orElse(GeneratedBeanTypeHierarchy.OBJECT_SUPERCLASS)));
            }
            type.getInterfaces().forEach(anInterface -> parents.add(ExpressionDef.constant(table.get(anInterface.getName()))));
            superTypes.add(TypeDef.Primitive.INT.array().instantiate(parents));
        }

        // the levels of every method are ordered by the hierarchy of the bean type, an inherited one included
        Set<String> order = breadthFirst(beanClassElement.getName(), hierarchy);
        List<ExpressionDef> methodLevels = new ArrayList<>(beanMethods.size());
        for (MethodElement method : beanMethods) {
            // the declaring type, then the types declaring a method it overrides, nearest first, each once; Object
            // is not in the table, so it is no level
            Set<Integer> levels = new LinkedHashSet<>();
            collectLevels(method, order, table, levels);
            methodLevels.add(TypeDef.Primitive.INT.array().instantiate(
                levels.stream().<ExpressionDef>map(ExpressionDef::constant).toList()));
        }

        return new TypeHierarchyDef(
            ClassTypeDef.of(AnnotationClassValue.class).array().instantiate(
                table.keySet().stream().map(loadClassValueExpressionFn).toList()),
            TypeDef.Primitive.INT.array(2).instantiate(superTypes),
            TypeDef.Primitive.INT.array(2).instantiate(methodLevels)
        );
    }

    /**
     * Collects the declaring levels of a method: the type declaring it, then the types declaring a method it
     * overrides, in the breadth first order of the hierarchy of the bean type, so a type the bean type extends or
     * implements directly comes before a type that one extends or implements in turn. An inherited method
     * overrides the methods of the interfaces the bean type introduces as well, these are levels too.
     */
    private static void collectLevels(MethodElement method, Set<String> order,
                                      Map<String, Integer> table, Set<Integer> levels) {
        Set<String> declaring = new HashSet<>();
        collectDeclaringTypes(method, declaring);
        Integer declaringType = table.get(method.getDeclaringType().getName());
        if (declaringType != null) {
            levels.add(declaringType);
        }
        for (String type : order) {
            if (declaring.remove(type)) {
                levels.add(table.get(type));
            }
        }
    }

    private static void collectDeclaringTypes(MethodElement method, Set<String> declaring) {
        if (!declaring.add(method.getDeclaringType().getName())) {
            return;
        }
        for (MethodElement overridden : method.getOverriddenMethods()) {
            collectDeclaringTypes(overridden, declaring);
        }
    }

    /**
     * The types of the hierarchy a type is or extends, breadth first: the super class before the interfaces.
     */
    private static Set<String> breadthFirst(String start, Map<String, ClassElement> hierarchy) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            ClassElement type = hierarchy.get(name);
            if (type == null || !visited.add(name)) {
                continue;
            }
            if (!type.isInterface()) {
                type.getSuperType().ifPresent(parent -> queue.add(parent.getName()));
            }
            type.getInterfaces().forEach(anInterface -> queue.add(anInterface.getName()));
        }
        return visited;
    }

    /**
     * Collects a type and, depth first, its super class and its interfaces, each once. Object is named as a super
     * class, it is not collected.
     */
    private static void collectHierarchy(ClassElement type, Map<String, ClassElement> types) {
        if (types.containsKey(type.getName()) || type.getName().equals(Object.class.getName())) {
            return;
        }
        types.put(type.getName(), type);
        if (!type.isInterface()) {
            type.getSuperType().ifPresent(parent -> collectHierarchy(parent, types));
        }
        type.getInterfaces().forEach(anInterface -> collectHierarchy(anInterface, types));
    }
}
