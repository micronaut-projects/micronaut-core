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
package io.micronaut.context;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Failure;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;

import java.util.*;
import java.util.stream.Stream;

/**
 * A Default context implementation.
 *
 * @param <B> The condition context type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultConditionContext<B extends AnnotationMetadataProvider> implements ConditionContext<B> {

    private final DefaultBeanContext beanContext;
    private final B component;
    private final List<Failure> failures = new ArrayList<>(2);
    private final BeanResolutionContext resolutionContext;

    /**
     * @param beanContext The bean context
     * @param component   The component type
     */
    DefaultConditionContext(DefaultBeanContext beanContext, B component, BeanResolutionContext resolutionContext) {
        this.beanContext = beanContext;
        this.component = component;
        this.resolutionContext = resolutionContext;
    }

    @Override
    public B getComponent() {
        return component;
    }

    @Override
    public BeanContext getBeanContext() {
        return beanContext;
    }

    @Override
    public BeanResolutionContext getBeanResolutionContext() {
        return resolutionContext;
    }

    @Override
    public ConditionContext<B> fail(@NonNull Failure failure) {
        failures.add(failure);
        return this;
    }

    @Override
    public String toString() {
        return component.toString();
    }

    @Override
    public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull BeanDefinition<T> definition) {
        return beanContext.getBean(definition);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return beanContext.getBean(resolutionContext, beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> Optional<T> findBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return beanContext.findBean(resolutionContext, beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> Collection<T> getBeansOfType(@NonNull Class<T> beanType) {
        return beanContext.getBeansOfType(resolutionContext, beanType);
    }

    @NonNull
    @Override
    public <T> Collection<T> getBeansOfType(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return beanContext.getBeansOfType(resolutionContext, beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> Stream<T> streamOfType(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return beanContext.streamOfType(resolutionContext, beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> T getProxyTargetBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return beanContext.getProxyTargetBean(beanType, qualifier);
    }

    @Override
    public boolean containsProperty(@NonNull String name) {
        if (beanContext instanceof PropertyResolver) {
            return ((PropertyResolver) beanContext).containsProperty(name);
        }
        return false;
    }

    @Override
    public boolean containsProperties(@NonNull String name) {
        if (beanContext instanceof PropertyResolver) {
            return ((PropertyResolver) beanContext).containsProperties(name);
        }
        return false;
    }

    @NonNull
    @Override
    public <T> Optional<T> getProperty(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        if (beanContext instanceof PropertyResolver) {
            return ((PropertyResolver) beanContext).getProperty(name, conversionContext);
        }
        return Optional.empty();
    }
}
