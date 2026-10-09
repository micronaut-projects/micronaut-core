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

import java.util.concurrent.atomic.AtomicReference;
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
    /** The bean the resolver was injected into, which the dependency graph records what it receives under; unset when not recorded. */
    private final AtomicReference<@Nullable Ownership> ownership = new AtomicReference<>();

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
        BeanRegistration<T> resolved = dependencies.resolve(context, null, resolution -> {
            BeanRegistration<T> registration = context.getBeanRegistration(resolution, type, qualifier);
            resolution.require(registration);
            return registration;
        });
        context.recordOwnedDependency(owner(), resolved);
        return resolved;
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
            ((DefaultBeanDependencyResolver) child.bean()).owner(owner(), false);
            resolution.addDependentBean(child);
            return (BeanDependencyGroup) child.bean();
        });
    }

    @Override
    public <T> BeanRegistration<T> createBeanRegistration(BeanDefinition<T> definition) {
        BeanRegistration<T> created = dependencies.resolve(context, null, resolution ->
            context.createFreshRegistration(resolution, definition));
        context.recordOwnedDependency(owner(), created);
        return created;
    }

    /**
     * Sets the bean the resolver was injected into, which owns what is resolved and created through it.
     *
     * @param owner The owner, or null when the dependency graph is not recorded
     * @param injected Whether this is the resolver injected into the owner, which releases the owner as its destruction begins
     */
    void owner(DefaultBeanDependencyGraph.@Nullable Owner owner, boolean injected) {
        ownership.set(owner == null ? null : new Ownership(owner, injected));
    }

    private DefaultBeanDependencyGraph.@Nullable Owner owner() {
        Ownership current = ownership.get();
        return current == null ? null : current.owner();
    }

    /**
     * Releases the owner's edges in the dependency graph, as the destruction of the owner begins.
     *
     * @param graph The graph
     */
    void releaseOwner(DefaultBeanDependencyGraph graph) {
        Ownership current = ownership.get();
        if (current != null && current.injected()) {
            graph.release(current.owner());
        }
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

    /**
     * The owner of a resolver, set once as the resolver is injected or its group opened.
     *
     * @param owner The bean the resolver was injected into
     * @param injected Whether this is the resolver injected into the owner, rather than a group it opened, and so releases the owner
     */
    private record Ownership(DefaultBeanDependencyGraph.Owner owner, boolean injected) {
    }
}
