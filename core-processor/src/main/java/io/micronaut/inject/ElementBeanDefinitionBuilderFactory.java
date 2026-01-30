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

import io.micronaut.context.bean.definition.builder.BeanDefinitionBuilderFactory;
import io.micronaut.context.bean.definition.builder.ConstructorDefinition;
import io.micronaut.context.bean.definition.builder.FieldDefinition;
import io.micronaut.context.bean.definition.builder.MethodDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Factory for creating {@link ElementBeanDefinitionBuilder} instances.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 5.0
 */
public interface ElementBeanDefinitionBuilderFactory<R> extends BeanDefinitionBuilderFactory<ClassElement, MethodElement, MethodElement, FieldElement, List<R>> {

    /**
     * Creates a bean definition builder for the given type.
     *
     * @param classElement The class element representing the bean
     * @return The bean definition builder
     */
    ElementBeanDefinitionBuilder<R> ofType(ClassElement classElement);

    /**
     * Creates a bean definition builder for the supplied factory method.
     *
     * @param methodElement The factory method element
     * @return The bean definition builder
     */
    ElementBeanDefinitionBuilder<R> factoryMethod(MethodElement methodElement);

    /**
     * Creates a bean definition builder for the supplied factory field.
     *
     * @param fieldElement The factory field element
     * @return The bean definition builder
     */
    ElementBeanDefinitionBuilder<R> factoryField(FieldElement fieldElement);

    /**
     * Creates an {@link ElementProxyBuilder} that wraps the supplied bean definition with AOP advice.
     *
     * @param classElement                The class element being advised
     * @param annotationMetadata          The annotation metadata describing the advice
     * @param targetBeanDefinitionBuilder The bean definition builder to proxy
     * @return The proxy builder
     */
    ElementProxyBuilder<R> aroundProxy(ClassElement classElement,
                                       AnnotationMetadata annotationMetadata,
                                       ElementBeanDefinitionBuilder<R> targetBeanDefinitionBuilder);

    /**
     * Creates an introduction proxy builder for the given target type.
     *
     * @param target The element being proxied
     * @return The proxy builder
     */
    ElementProxyBuilder<R> introductionProxy(ClassElement target);

    /**
     * Creates an introduction proxy builder using the provided metadata.
     *
     * @param proxyName              The proxy class name
     * @param proxyAnnotationMetadata The proxy annotation metadata
     * @return The proxy builder
     */
    ElementProxyBuilder<R> introductionProxy(String proxyName,
                                             AnnotationMetadata proxyAnnotationMetadata);

    /**
     * Creates an introduction proxy builder using the provided metadata and bean type.
     *
     * @param proxyName               The proxy class name
     * @param proxyAnnotationMetadata The proxy annotation metadata
     * @param beanType                The bean type exposed by the proxy
     * @return The proxy builder
     */
    default ElementProxyBuilder<R> introductionProxy(String proxyName,
                                                     AnnotationMetadata proxyAnnotationMetadata,
                                                     ClassElement beanType) {
        return introductionProxy(proxyName, proxyAnnotationMetadata);
    }

    @Override
    ElementBeanDefinitionBuilder<R> constructor(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition);

    @Override
    ElementBeanDefinitionBuilder<R> constructor(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                                @Nullable String beanDefinitionName,
                                                @Nullable AnnotationMetadata annotationMetadata);

    @Override
    ElementBeanDefinitionBuilder<R> factoryMethod(MethodDefinition<ClassElement, MethodElement> methodDefinition);

    @Override
    ElementBeanDefinitionBuilder<R> factoryField(FieldDefinition<ClassElement, FieldElement> fieldDefinition);

}
