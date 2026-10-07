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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The expressions of the arguments of the {@link GeneratedBeanTypeHierarchy} an introspection generates. Every
 * type is written once and referenced by its index: depth first, the super class before the interfaces. The
 * bean methods the bean type declares itself are referenced by their index among the bean methods.
 *
 * @param types The types, each once
 * @param superTypes For each type, the indexes of the types it extends and implements
 * @param declaredMethods The indexes of the bean methods the bean type declares itself
 * @author Denis Stepanov
 * @since 5.3.0
 */
record TypeHierarchyDef(ExpressionDef types, ExpressionDef superTypes, ExpressionDef declaredMethods) {

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

        List<ExpressionDef> declaredMethods = new ArrayList<>();
        for (int i = 0; i < beanMethods.size(); i++) {
            if (beanMethods.get(i).getDeclaringType().getName().equals(beanClassElement.getName())) {
                declaredMethods.add(ExpressionDef.constant(i));
            }
        }

        return new TypeHierarchyDef(
            ClassTypeDef.of(AnnotationClassValue.class).array().instantiate(
                table.keySet().stream().map(loadClassValueExpressionFn).toList()),
            TypeDef.Primitive.INT.array(2).instantiate(superTypes),
            TypeDef.Primitive.INT.array().instantiate(declaredMethods)
        );
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
