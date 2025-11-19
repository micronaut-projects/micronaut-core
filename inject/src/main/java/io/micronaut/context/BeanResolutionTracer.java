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
package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import java.util.Collection;

/**
 * API for tracing bean resolution.
 */
@Internal
sealed interface BeanResolutionTracer permits ConsoleBeanResolutionTracer {

    /**
     * Tracing the starting configuration of the context.
     * @param environment The environment.
     * @param beanReferences The loaded bean references
     * @param disabledBeans The disabled beans
     */
    default void traceInitialConfiguration(
        @NonNull Environment environment,
        @NonNull Collection<BeanDefinitionReference<Object>> beanReferences,
        @NonNull Collection<DisabledBean<?>> disabledBeans) {
        // no-op
    }

    /**
     * Start tracing the creation of a bean.
     *
     * @param resolutionContext The resolution context
     * @param beanDefinition The bean definition
     * @param beanType The bean type
     */
    void traceBeanCreation(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull BeanDefinition<?> beanDefinition,
        @NonNull Argument<?> beanType);

    /**
     * Trace when a bean was resolved for a type.
     * @param resolutionContext The resolution context
     * @param beanType The bean type
     * @param qualifier The qualifier (can be {@code null})
     * @param bean The bean (can be {@code null} if a factory resolved to {@code null}
     * @param <T> The bean generic type
     */
    default <T> void traceBeanResolved(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull Argument<T> beanType,
        @Nullable Qualifier<T> qualifier,
        @Nullable T bean
    ) {
        // no-op
    }

    /**
     * Callback to trace when bean resolution detected disabled beans that
     * match the type and qualifier.
     * @param resolutionContext The resolution context
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param disabledBeanMessage The message
     * @param <T> The bean generic type
     */
    default <T> void traceBeanDisabled(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull Argument<T> beanType,
        @NonNull Qualifier<T> qualifier,
        @NonNull String disabledBeanMessage) {
        // no-op
    }

    /**
     * Trace when a value is resolved.
     *
     * @see Environment#getProperty(String, Argument)
     *
     * @param resolutionContext The resolution context
     * @param argument The argument type.
     * @param property The property
     * @param value The value
     * @param <T> The value generic type
     */
    default <T> void traceValueResolved(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull Argument<T> argument,
        @NonNull String property,
        @NonNull T value
    ) {
        // no-op
    }

    /**
     * Trace when a bean has been created and all bean creation listeners fired.
     * @param resolutionContext The resolution context
     * @param beanDefinition The bean definition
     * @param <T> The bean type
     */
    <T> void traceBeanCreated(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull BeanDefinition<T> beanDefinition);

    /**
     * Trace when a bean is about to be injected.
     *
     * <p>Called prior to injection occurring and any potential bean creation that it may trigger.</p>
     *
     * @param resolutionContext The resolution context
     * @param segment The segment that represents the injection point
     * @param <B> The bean type
     * @param <T> The injection point type
     */
    <B, T> void traceInjectBean(
        @NonNull BeanResolutionContext resolutionContext,
        @NonNull BeanResolutionContext.Segment<B, T> segment);

    /**
     * Called on the completion of resolution of an injection point.
     * @param resolutionContext The resolution context
     * @param segment The segment
     * @param <B> The bean type
     * @param <T> The injection point type
     */
    default <B, T> void traceInjectComplete(
        BeanResolutionContext resolutionContext,
        BeanResolutionContext.Segment<B, T> segment) {
        // no-op
    }

    /**
     * Trace the shutdown of the context.
     * @param beanContext The bean context
     */
    default void traceContextShutdown(BeanContext beanContext) {
        // no-op
    }
}
