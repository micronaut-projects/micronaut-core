/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Failure;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
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
final class DefaultConditionContext<B extends AnnotationMetadataProvider> implements ConditionContext<B> {

    private final B component;
    private final List<Failure> failures = new ArrayList<>(0);
    private final BeanResolutionContext originalResolutionContext;
    private final BeanResolutionContext resolutionContext;

    /**
     * @param beanContext The bean context
     * @param component   The component type
     * @param resolutionContext The resolution context
     */
    DefaultConditionContext(@NonNull BeanContext beanContext, @NonNull B component, @Nullable BeanResolutionContext resolutionContext) {
        this.component = component;
        this.originalResolutionContext = resolutionContext;
        this.resolutionContext = resolutionContext == null ? new DefaultBeanResolutionContext(beanContext, null) : resolutionContext;
        // Note: The behavior of the conditions is to skip the check if the resolutionContext is null.
        // This is because the conditions are evaluated before the context is initialized.
    }

    @Override
    public B getComponent() {
        return component;
    }

    @Override
    public BeanContext getBeanContext() {
        return resolutionContext.getContext();
    }

    @Override
    public BeanResolutionContext getBeanResolutionContext() {
        return originalResolutionContext;
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

    @Override
    public <K> Collection<BeanDefinition<K>> findBeanDefinitions(Class<K> beanType) {
        if (resolutionContext instanceof AbstractBeanResolutionContext abstractBeanResolutionContext) {
            if (component instanceof BeanDefinition<?> beanDefinition) {
                return abstractBeanResolutionContext.findBeanDefinitions(Argument.of(beanType), beanDefinition);
            }
            return abstractBeanResolutionContext.findBeanDefinitions(Argument.of(beanType), null);
        }
        return List.of();
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull BeanDefinition<T> definition) {
        return resolutionContext.getBean(definition);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.getBean(beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> T getBean(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.getBean(beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> Optional<T> findBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.findBean(Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> Collection<T> getBeansOfType(@NonNull Class<T> beanType) {
        return resolutionContext.getBeansOfType(beanType);
    }

    @NonNull
    @Override
    public <T> Collection<T> getBeansOfType(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.getBeansOfType(Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> Collection<T> getBeansOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.getBeansOfType(beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> Stream<T> streamOfType(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.streamOfType(Argument.of(beanType), qualifier);
    }

    @NonNull
    @Override
    public <T> Stream<T> streamOfType(@NonNull Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.streamOfType(beanType, qualifier);
    }

    @NonNull
    @Override
    public <T> T getProxyTargetBean(@NonNull Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return resolutionContext.getProxyTargetBean(beanType, qualifier);
    }

    @Override
    public <T> Optional<T> findBean(Argument<T> beanType, Qualifier<T> qualifier) {
        return resolutionContext.findBean(beanType, qualifier);
    }

    @Override
    public boolean containsProperty(@NonNull String name) {
        PropertyResolver propertyResolver = resolutionContext.getPropertyResolver();
        if (propertyResolver != null) {
            return propertyResolver.containsProperty(name);
        }
        return false;    }

    @Override
    public boolean containsProperties(@NonNull String name) {
        PropertyResolver propertyResolver = resolutionContext.getPropertyResolver();
        if (propertyResolver != null) {
            return propertyResolver.containsProperties(name);
        }
        return false;
    }

    @NonNull
    @Override
    public <T> Optional<T> getProperty(@NonNull String name, @NonNull ArgumentConversionContext<T> conversionContext) {
        PropertyResolver propertyResolver = resolutionContext.getPropertyResolver();
        if (propertyResolver != null) {
            return propertyResolver.getProperty(name, conversionContext);
        }
        return Optional.empty();
    }

    @Override
    public Collection<List<String>> getPropertyPathMatches(String pathPattern) {
        PropertyResolver propertyResolver = resolutionContext.getPropertyResolver();
        if (propertyResolver != null) {
            return propertyResolver.getPropertyPathMatches(pathPattern);
        }
        return Collections.emptyList();
    }

}
