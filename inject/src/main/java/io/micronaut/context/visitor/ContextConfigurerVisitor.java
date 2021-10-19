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

import io.micronaut.context.ApplicationContextCustomizer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.Set;

/**
 * This visitor is responsible for generating service files for classes
 * annotated with {@link ContextConfigurer} or {@link io.micronaut.context.annotation.MicronautApplication}.
 *
 * @since 3.2
 */
public class ContextConfigurerVisitor implements TypeElementVisitor<ContextConfigurer, Object> {
    private static final Set<String> SUPPORTED_SERVICE_TYPES = Collections.singleton(
            ApplicationContextCustomizer.class.getName()
    );

    @Override
    public String getElementType() {
        return ContextConfigurer.class.getName();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        element.getInterfaces()
                .stream()
                .map(Element::getName)
                .filter(SUPPORTED_SERVICE_TYPES::contains)
                .forEach(serviceType -> context.visitServiceDescriptor(serviceType, element.getName()));
    }

}
