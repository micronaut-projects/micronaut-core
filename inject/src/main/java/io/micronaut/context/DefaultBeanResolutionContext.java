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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Default implementation of {@link BeanResolutionContext}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class DefaultBeanResolutionContext extends AbstractBeanResolutionContext {
    @Nullable
    private final DefaultBeanDependencies dependencies;
    private final @Nullable Thread destructionThread;
    private volatile boolean closed;
    @Nullable
    private List<BeanRegistration<?>> required;
    private final Map<BeanIdentifier, BeanRegistration<?>> beansInCreation = new ConcurrentHashMap<>(5);

    /**
     * @param context        The bean context
     * @param rootDefinition The bean root definition
     */
    public DefaultBeanResolutionContext(BeanContext context, @Nullable BeanDefinition<?> rootDefinition) {
        this(context, rootDefinition, null);
    }

    DefaultBeanResolutionContext(BeanContext context, @Nullable BeanDefinition<?> rootDefinition, @Nullable DefaultBeanDependencies dependencies) {
        this(context, rootDefinition, dependencies, false);
    }

    DefaultBeanResolutionContext(BeanContext context, @Nullable BeanDefinition<?> rootDefinition,
                                 @Nullable DefaultBeanDependencies dependencies, boolean destruction) {
        super((DefaultBeanContext) context, rootDefinition);
        this.dependencies = dependencies;
        this.destructionThread = destruction ? Thread.currentThread() : null;
    }

    boolean isDestructionInvocationActive() {
        return !closed && destructionThread == Thread.currentThread() && context.isContextConfigured();
    }

    @Override
    public <R> R withDependencies(Function<BeanDependencyGroup, R> action) {
        if (closed) {
            throw new IllegalStateException("Cannot resolve dependencies through a closed resolution context");
        }
        if (destructionThread == null) {
            return context.withDependencies(action);
        }
        if (!isDestructionInvocationActive()) {
            throw new IllegalStateException("Destruction dependencies are only available during the synchronous destruction invocation");
        }
        try (BeanDependencyGroup group = new DefaultBeanDependencyResolver(context, new DefaultBeanDependencies(this))) {
            return action.apply(group);
        }
    }

    /**
     * Opens a group for the caller to close, which resolves as {@link #withDependencies(Function)} does: during the
     * synchronous destruction invocation for a context created for one, as an independent group otherwise.
     *
     * @return The group
     */
    BeanDependencyGroup newDependencyGroup() {
        if (destructionThread == null) {
            return context.createDependencyGroup();
        }
        return new DefaultBeanDependencyResolver(context, new DefaultBeanDependencies(this));
    }

    void require(BeanRegistration<?> registration) {
        if (required == null) {
            required = new ArrayList<>(2);
        }
        required.add(registration);
    }

    List<BeanRegistration<?>> requiredBeans() {
        return required == null ? List.of() : required;
    }

    @Override
    public <I> Collection<BeanRegistration<I>> getInterceptorRegistrations(Argument<I> interceptorType, @Nullable Qualifier<I> binding) {
        Collection<BeanRegistration<I>> registrations = super.getInterceptorRegistrations(interceptorType, binding);
        if (dependencies != null && getPath().isEmpty()) {
            registrations.forEach(this::require);
        }
        return registrations;
    }

    @Override
    @Nullable
    <I> BeanRegistration<I> findInterceptor(BeanDefinition<I> definition) {
        BeanRegistration<I> created = super.findInterceptor(definition);
        if (created != null || dependencies == null || !getPath().isEmpty()) {
            // A nested bean owns its own interceptors, even when resolved during another bean's selection.
            return created;
        }
        return findInterceptor(dependencies.dependentBeans(), definition);
    }

    @Override
    public BeanResolutionContext copy() {
        // Destruction permission belongs to this invocation and is never transferred to a copy.
        DefaultBeanResolutionContext copy = new DefaultBeanResolutionContext(context, rootDefinition);
        copy.copyStateFrom(this);
        return copy;
    }

    @Override
    public void close() {
        closed = true;
        beansInCreation.clear();
    }

    @Override
    public <T> void addInFlightBean(BeanIdentifier beanIdentifier, BeanRegistration<T> beanRegistration) {
        beansInCreation.put(beanIdentifier, beanRegistration);
    }

    @Override
    public void removeInFlightBean(BeanIdentifier beanIdentifier) {
        beansInCreation.remove(beanIdentifier);
    }

    @Nullable
    @Override
    public <T> BeanRegistration<T> getInFlightBean(BeanIdentifier beanIdentifier) {
        //noinspection unchecked
        return (BeanRegistration<T>) beansInCreation.get(beanIdentifier);
    }
}
