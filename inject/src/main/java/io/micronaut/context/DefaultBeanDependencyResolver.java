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
package io.micronaut.context;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * An injected resolver whose dependencies are held by its own registration, which is a dependent of the consumer.
 *
 * @since 5.3.0
 */
@Internal
final class DefaultBeanDependencyResolver implements BeanDependencyGroup, BeanDependencies {
    private final DefaultBeanContext context;
    final DefaultBeanDependencies dependencies;

    DefaultBeanDependencyResolver(DefaultBeanContext context) {
        this(context, new DefaultBeanDependencies());
    }

    DefaultBeanDependencyResolver(DefaultBeanContext context, DefaultBeanDependencies dependencies) {
        this.context = context;
        this.dependencies = dependencies;
    }

    @Override
    public <T> T getBean(Argument<T> type, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(type, qualifier).getBean();
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(Argument<T> type, @Nullable Qualifier<T> qualifier) {
        return dependencies.resolve(context, null, resolution -> {
            BeanRegistration<T> registration = context.getBeanRegistration(resolution, type, qualifier);
            resolution.require(registration);
            return registration;
        });
    }

    @Override
    public <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<? extends T> definition, Argument<T> type) {
        return dependencies.resolve(context, null, resolution -> {
            BeanRegistration<T> registration = context.getBeanRegistration(resolution, definition, type);
            resolution.require(registration);
            return registration;
        });
    }

    @Override
    public BeanDependencyGroup createGroup() {
        return dependencies.resolve(context, null, resolution -> {
            BeanRegistration<BeanDependencyResolver> child = context.newDependencyGroupRegistration(dependencies.destructionContext);
            resolution.addDependentBean(child);
            return (BeanDependencyGroup) child.bean();
        });
    }

    @Override
    public <T> BeanRegistration<T> createBeanRegistration(BeanDefinition<T> definition) {
        return dependencies.resolve(context, null, resolution ->
            context.createFreshRegistration(resolution, definition));
    }

    @Override
    public boolean destroy(BeanRegistration<?> registration) {
        if (dependencies.remove(registration)) {
            context.destroyDependentBean(registration);
            return true;
        }
        return false;
    }

    @Override
    public boolean isClosed() {
        return dependencies.isClosing();
    }

    @Override
    public void close() {
        dependencies.close(context);
    }

    @Override
    public <S> @Nullable S resolveDependencies(BeanLocator context, @Nullable BeanDefinition<?> definition,
                                               Function<BeanResolutionContext, S> operation) {
        return dependencies.resolveDependencies(context, definition, operation);
    }
}
