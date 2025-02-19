/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context.visitor;

import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.Set;

/**
 * This visitor is responsible for generating service files for classes
 * annotated with {@link ContextConfigurer}.
 *
 * @since 3.2
 */
@Internal
public class ContextConfigurerVisitor implements TypeElementVisitor<ContextConfigurer, Object> {
    private static final Set<String> SUPPORTED_SERVICE_TYPES = Collections.singleton(
            ApplicationContextConfigurer.class.getName()
    );

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public String getElementType() {
        return ContextConfigurer.class.getName();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        assertNoConstructorForContextAnnotation(element);
        element.getInterfaces()
                .stream()
                .map(Element::getName)
                .filter(SUPPORTED_SERVICE_TYPES::contains)
                .forEach(serviceType -> context.visitServiceDescriptor(serviceType, element.getName(), element));
    }

    /**
     * Checks that a class annotated with {@link ContextConfigurer} doesn't have any constructor
     * with parameters, which is unsupported.
     * @param element the class to check
     */
    public static void assertNoConstructorForContextAnnotation(ClassElement element) {
        element.getEnclosedElements(ElementQuery.CONSTRUCTORS)
                .stream()
                .filter(e -> e.getParameters().length > 0)
                .findAny()
                .ifPresent(e -> {
                    throw typeShouldNotHaveConstructorsWithArgs(element.getName());
                });
    }

    @NonNull
    private static RuntimeException typeShouldNotHaveConstructorsWithArgs(String type) {
        return new IllegalStateException(type + " is annotated with @ContextConfigurer but has at least one constructor with arguments, which isn't supported. To resolve this create a separate class with no constructor arguments annotated with @ContextConfigurer, which sole role is configuring the application context.");
    }
}
