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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Failure;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The context a custom condition of a {@code @Requires} is evaluated in when the configuration staleness check asks
 * whether a refresh flipped it: the running context answers the beans and the properties, as it does when a bean
 * reference is checked, without a resolution context, and a failure is only recorded, never tracked as a disabled bean.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class ProbeConditionContext implements ConditionContext<AnnotationMetadataProvider> {

    private final ApplicationContext context;
    private final AnnotationMetadataProvider component;
    private final List<Failure> failures = new ArrayList<>(0);

    ProbeConditionContext(ApplicationContext context, AnnotationMetadataProvider component) {
        this.context = context;
        this.component = component;
    }

    @Override
    public AnnotationMetadataProvider getComponent() {
        return component;
    }

    @Override
    public BeanContext getBeanContext() {
        return context;
    }

    @Override
    public @Nullable BeanResolutionContext getBeanResolutionContext() {
        return null;
    }

    @Override
    public <K> Collection<BeanDefinition<K>> findBeanDefinitions(Class<K> beanType) {
        return context.getBeanDefinitions(beanType);
    }

    @Override
    public ConditionContext<AnnotationMetadataProvider> fail(Failure failure) {
        failures.add(failure);
        return this;
    }

    @Override
    public List<Failure> getFailures() {
        return Collections.unmodifiableList(failures);
    }

    @Override
    public <T> T getBean(BeanDefinition<T> definition) {
        return context.getBean(definition);
    }

    @Override
    public <T> T getBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.getBean(beanType, qualifier);
    }

    @Override
    public <T> Optional<T> findBean(Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.findBean(beanType, qualifier);
    }

    @Override
    public <T> Optional<T> findBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.findBean(beanType, qualifier);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType) {
        return context.getBeansOfType(beanType);
    }

    @Override
    public <T> Collection<T> getBeansOfType(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.getBeansOfType(beanType, qualifier);
    }

    @Override
    public <T> Stream<T> streamOfType(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.streamOfType(beanType, qualifier);
    }

    @Override
    public <T> T getProxyTargetBean(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return context.getProxyTargetBean(beanType, qualifier);
    }

    @Override
    public boolean containsProperty(String name) {
        return context.containsProperty(name);
    }

    @Override
    public boolean containsProperties(String name) {
        return context.containsProperties(name);
    }

    @Override
    public <T> Optional<T> getProperty(String name, ArgumentConversionContext<T> conversionContext) {
        return context.getProperty(name, conversionContext);
    }

    @Override
    public Collection<List<String>> getPropertyPathMatches(String pathPattern) {
        return context.getPropertyPathMatches(pathPattern);
    }
}
