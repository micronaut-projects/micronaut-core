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
package io.micronaut.inject;

import io.micronaut.context.bean.definition.builder.BeanDefinitionBuilder;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint;
import io.micronaut.context.bean.definition.builder.FieldDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.OriginatingElements;

import java.util.List;

import static io.micronaut.inject.utils.BeanInjectionUtils.createFieldDefinition;
import static io.micronaut.inject.utils.BeanInjectionUtils.createMethodDefinition;

/**
 * Micronaut {@link BeanDefinitionBuilder} variant that operates on {@link io.micronaut.inject.ast.Element} inputs.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 5.0
 */
public interface ElementBeanDefinitionBuilder<R> extends BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<R>>, OriginatingElements, Toggleable {

    /**
     * Registers a {@code @PostConstruct} method on the bean definition.
     *
     * @param methodElement      The source method
     * @param reflectionRequired Whether reflective invocation is required
     * @param visitorContext     The visitor context
     * @return This builder
     */
    default ElementBeanDefinitionBuilder<R> addPostConstruct(MethodElement methodElement, boolean reflectionRequired, VisitorContext visitorContext) {
        addPostConstruct(createMethodDefinition(methodElement.getOwningType(), methodElement, methodElement, reflectionRequired, visitorContext));
        return this;
    }

    /**
     * Registers a {@code @PreDestroy} method on the bean definition.
     *
     * @param methodElement      The source method
     * @param reflectionRequired Whether reflective invocation is required
     * @param visitorContext     The visitor context
     * @return This builder
     */
    default ElementBeanDefinitionBuilder<R> addPreDestroy(MethodElement methodElement, boolean reflectionRequired, VisitorContext visitorContext) {
        addPreDestroy(createMethodDefinition(methodElement.getOwningType(), methodElement, methodElement, reflectionRequired, visitorContext));
        return this;
    }

    /**
     * Adds a field injection point to the bean definition.
     *
     * @param fieldElement       The field element
     * @param reflectionRequired Whether reflective access is required
     * @param visitorContext     The visitor context
     * @return This builder
     */
    default ElementBeanDefinitionBuilder<R> addFieldInjection(FieldElement fieldElement, boolean reflectionRequired, VisitorContext visitorContext) {
        addFieldInjection(createFieldDefinition(fieldElement.getOwningType(), fieldElement, reflectionRequired, visitorContext));
        return this;
    }

    /**
     * Adds a method injection point to the bean definition.
     *
     * @param methodElement      The method element
     * @param reflectionRequired Whether reflective invocation is required
     * @param visitorContext     The visitor context
     * @return This builder
     */
    default ElementBeanDefinitionBuilder<R> addMethodInjection(MethodElement methodElement, boolean reflectionRequired, VisitorContext visitorContext) {
        addMethodInjection(createMethodDefinition(methodElement.getOwningType(), methodElement, methodElement, reflectionRequired, visitorContext));
        return this;
    }

    /**
     * Adds a field property injection point (e.g. {@code @Property}) to the bean definition.
     *
     * @param fieldElement       The field element
     * @param annotationMetadata The annotation metadata associated with the injection
     * @param reflectionRequired Whether reflective access is required
     * @param isOptional         Whether the injection is optional
     * @param visitorContext     The visitor context
     * @return This builder
     */
    default ElementBeanDefinitionBuilder<R> addFieldPropertyInjection(FieldElement fieldElement,
                                           AnnotationMetadata annotationMetadata,
                                           boolean reflectionRequired,
                                           boolean isOptional,
                                           VisitorContext visitorContext) {
        BeanDefinitionInjectionPoint<ClassElement> injectionPoint = BeanInjectionUtils.createValueInjectionPoint(
            fieldElement.getOwningType(),
            fieldElement.getGenericType(),
            annotationMetadata,
            reflectionRequired,
            fieldElement.getName(),
            visitorContext
        );
        addFieldInjection(
            new FieldDefinition<>(
                fieldElement,
                annotationMetadata,
                injectionPoint,
                reflectionRequired,
                isOptional)
        );
        return this;
    }

    /**
     * Adds a field property injection point inferring optionality from {@link io.micronaut.inject.InjectionPoint}.
     *
     * @param fieldElement       The field element
     * @param annotationMetadata The annotation metadata associated with the injection
     * @param reflectionRequired Whether reflective access is required
     * @param visitorContext     The visitor context
     * @return This builder
     */
    default ElementBeanDefinitionBuilder<R> addFieldPropertyInjection(FieldElement fieldElement,
                                           AnnotationMetadata annotationMetadata,
                                           boolean reflectionRequired, VisitorContext visitorContext) {
        return addFieldPropertyInjection(fieldElement, annotationMetadata, reflectionRequired, !InjectionPoint.isInjectionRequired(fieldElement), visitorContext);
    }
}
