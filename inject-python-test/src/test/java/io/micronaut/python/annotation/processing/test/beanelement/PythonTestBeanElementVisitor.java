/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.annotation.processing.test.beanelement;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class PythonTestBeanElementVisitor implements BeanElementVisitor<Prototype> {
    public static boolean enabled;
    public static boolean produceAssociatedBean;

    private BeanElement theBeanElement;
    private Set<String> beanTypeNames = Set.of();
    private Set<String> injectionPointNames = Set.of();
    private boolean initialized;
    private boolean terminated;

    @Override
    public BeanElement visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        if (!enabled) {
            return beanElement;
        }
        Element producingElement = beanElement.getProducingElement();
        if (producingElement instanceof MemberElement memberElement) {
            producingElement = memberElement.getDeclaringType();
        }
        String name = producingElement.getName();
        if (name.startsWith("python.Test")) {
            theBeanElement = beanElement;
            beanTypeNames = beanElement.getBeanTypes()
                .stream()
                .map(Element::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            injectionPointNames = beanElement.getInjectionPoints()
                .stream()
                .map(Element::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (name.equals("python.Excluded")) {
            return null;
        }
        return beanElement;
    }

    @Override
    public void start(VisitorContext visitorContext) {
        if (enabled) {
            theBeanElement = null;
            initialized = true;
            terminated = false;
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (!enabled) {
            return;
        }
        if (produceAssociatedBean && theBeanElement != null) {
            visitorContext.getClassElement(String.class)
                .ifPresent(element -> theBeanElement.addAssociatedBean(element, visitorContext)
                    .createWith(
                        element.getEnclosedElement(
                                ElementQuery.of(ConstructorElement.class)
                                    .filter(constructor -> constructor.hasParameters()
                                        && constructor.getParameters()[0].getType().isAssignable(String.class))
                            )
                            .orElseThrow(() -> new IllegalStateException("Unknown constructor"))
                    )
                    .withParameters(parameters -> parameters[0].injectValue("test")));
        }
        terminated = true;
    }

    public BeanElement getTheBeanElement() {
        return theBeanElement;
    }

    public Set<String> getBeanTypeNames() {
        return beanTypeNames;
    }

    public Set<String> getInjectionPointNames() {
        return injectionPointNames;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void reset() {
        theBeanElement = null;
        beanTypeNames = Set.of();
        injectionPointNames = Set.of();
        initialized = false;
        terminated = false;
        produceAssociatedBean = false;
    }
}
