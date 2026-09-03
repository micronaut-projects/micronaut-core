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
package io.micronaut.inject.visitor;

import io.micronaut.context.beans.definition.BeanDefinitionInjectionPoint;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.ClassElement;

import java.util.Optional;

/**
 * Resolves injection point shapes which are not part of the core Java type model.
 *
 * <p>Language integrations can use this extension point to map collection-like,
 * optional, or other language-specific types to one of the standard
 * {@link BeanDefinitionInjectionPoint} implementations. Returning an empty
 * optional lets the core processor treat the requested type as a single bean.</p>
 *
 * @author Graeme Rocher
 * @since 5.2.0
 */
@Experimental
@FunctionalInterface
public interface BeanDefinitionInjectionPointResolver {

    /**
     * The default resolver used by visitor contexts that do not provide a
     * language-specific injection model.
     */
    BeanDefinitionInjectionPointResolver NONE = (beanType, requestedType, annotationMetadata, parameterName, visitorContext) -> Optional.empty();

    /**
     * Resolves an injection point for a requested type.
     *
     * @param beanType         The bean declaring the injection point
     * @param requestedType    The requested type
     * @param annotationMetadata The injection point annotation metadata
     * @param parameterName    The parameter, field, or property name
     * @param visitorContext   The visitor context
     * @return A standard injection point, or empty when the resolver does not
     * support the requested type
     */
    Optional<BeanDefinitionInjectionPoint<ClassElement>> resolve(
        ClassElement beanType,
        ClassElement requestedType,
        AnnotationMetadata annotationMetadata,
        String parameterName,
        VisitorContext visitorContext
    );
}
