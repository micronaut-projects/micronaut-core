/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.processor;

import io.micronaut.context.BeanContext;

import java.lang.annotation.Annotation;

/**
 * A bean definition processor is a processor that is called once for each bean annotated with the given annotation type.
 *
 * <p>The {@link #process(io.micronaut.inject.BeanDefinition, Object)} method will receive each {@link io.micronaut.inject.BeanDefinition} and the {@link BeanContext} as arguments.</p>
 *
 * <p>If the processor needs to be executed as startup it should be define as a {@link io.micronaut.context.annotation.Context} scoped bean.</p>
 *
 * @param <A> The annotation type
 * @author graemerocher
 * @since 1.0.3
 */
public interface BeanDefinitionProcessor<A extends Annotation> extends AnnotationProcessor<A, BeanContext> {
}
