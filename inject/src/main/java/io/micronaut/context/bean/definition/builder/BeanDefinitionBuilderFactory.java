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
package io.micronaut.context.bean.definition.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.Nullable;

/**
 * Factory for creating {@link BeanDefinitionBuilder} instances.
 *
 * @param <C>   The class type
 * @param <Ctr> The constructor element type
 * @param <M>   The method element type
 * @param <F>   The field type
 * @param <R>   The builder result
 * @author Denis Stepanov
 * @since 5.0
 */
public interface BeanDefinitionBuilderFactory<C, Ctr, M, F, R> {

    /**
     * Creates a builder backed by the given constructor definition.
     *
     * @param constructorDefinition The constructor definition
     * @return The bean definition builder
     */
    BeanDefinitionBuilder<C, M, F, R> constructor(ConstructorDefinition<C, Ctr> constructorDefinition);

    /**
     * Creates a builder backed by the given constructor definition.
     *
     * @param constructorDefinition The constructor definition
     * @param beanDefinitionName    An explicit bean definition name
     * @param annotationMetadata    Annotation metadata to associate
     * @return The bean definition builder
     */
    BeanDefinitionBuilder<C, M, F, R> constructor(ConstructorDefinition<C, Ctr> constructorDefinition,
                                               @Nullable String beanDefinitionName,
                                               @Nullable AnnotationMetadata annotationMetadata);

    /**
     * Creates a builder backed by the given factory method.
     *
     * @param methodDefinition The factory method definition
     * @return The bean definition builder
     */
    BeanDefinitionBuilder<C, M, F, R> factoryMethod(MethodDefinition<C, M> methodDefinition);

    /**
     * Creates a builder backed by the given factory field.
     *
     * @param fieldDefinition The factory field definition
     * @return The bean definition builder
     */
    BeanDefinitionBuilder<C, M, F, R> factoryField(FieldDefinition<C, F> fieldDefinition);

}
