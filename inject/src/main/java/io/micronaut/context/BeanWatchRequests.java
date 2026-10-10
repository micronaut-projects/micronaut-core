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

import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanExecutableMethod;
import io.micronaut.context.watch.BeanInstanceChange;
import io.micronaut.context.watch.BeanInstanceWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ClassChangeWatchRequest;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.context.watch.ConfigurationWatchRequest;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.context.watch.DefinitionWatchRequest;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.context.watch.InstanceWatchRequest;
import io.micronaut.context.watch.MethodWatchRequest;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import io.micronaut.context.watch.ResourceSelector;
import io.micronaut.context.watch.ResourceWatchRequest;
import io.micronaut.context.watch.ResourceWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * The watch requests of a {@link DefaultBeanContext}: each collects what a watch selects and registers it with
 * the {@link BeanWatchRegistry} when a terminal operation is called, from a snapshot of the request.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class BeanWatchRequests {

    private static final String WATCHER = "watcher";

    private BeanWatchRequests() {
    }

    /**
     * Requires a handler for a terminal operation without a watcher.
     */
    private static void requireHandler(ChangeHandlers<?, ?> handlers) {
        if (handlers.isEmpty()) {
            throw new IllegalStateException("No handler was added to the watch request: add one with onAdded, onRemoved or onReplaced, or pass a watcher");
        }
    }

    /**
     * Combines the qualifiers of a request: all of them must accept a definition.
     */
    @SuppressWarnings("unchecked")
    private static <T> @Nullable Qualifier<T> combine(List<Qualifier<T>> qualifiers) {
        return switch (qualifiers.size()) {
            case 0 -> null;
            case 1 -> qualifiers.get(0);
            default -> Qualifiers.byQualifiers(qualifiers.toArray(new Qualifier[0]));
        };
    }

    static final class DefinitionRequest<T> implements DefinitionWatchRequest<T> {
        private final BeanWatchRegistry registry;
        private final Argument<T> beanType;
        private final List<Qualifier<T>> qualifiers = new ArrayList<>(1);
        private final ChangeHandlers<BeanDefinition<T>, BeanDefinitionChange.Replacement<T>> handlers = new ChangeHandlers<>();

        DefinitionRequest(BeanWatchRegistry registry, Argument<T> beanType) {
            this.registry = registry;
            this.beanType = Objects.requireNonNull(beanType, "beanType");
        }

        @Override
        public DefinitionWatchRequest<T> qualifier(Qualifier<T> qualifier) {
            qualifiers.add(Objects.requireNonNull(qualifier, "qualifier"));
            return this;
        }

        @Override
        public DefinitionWatchRequest<T> stereotype(Class<? extends Annotation> stereotype) {
            return qualifier(Qualifiers.byStereotype(Objects.requireNonNull(stereotype, "stereotype")));
        }

        @Override
        public DefinitionWatchRequest<T> stereotype(String annotationName) {
            return qualifier(Qualifiers.byStereotype(Objects.requireNonNull(annotationName, "annotationName")));
        }

        @Override
        public DefinitionWatchRequest<T> onAdded(Consumer<? super BeanDefinition<T>> handler) {
            handlers.onAdded(handler);
            return this;
        }

        @Override
        public DefinitionWatchRequest<T> onRemoved(Consumer<? super BeanDefinition<T>> handler) {
            handlers.onRemoved(handler);
            return this;
        }

        @Override
        public DefinitionWatchRequest<T> onReplaced(Consumer<? super BeanDefinitionChange.Replacement<T>> handler) {
            handlers.onReplaced(handler);
            return this;
        }

        @Override
        public BeanWatch watch() {
            requireHandler(handlers);
            return registry.watchDefinitions(beanType, combine(qualifiers), null, handlers.snapshot());
        }

        @Override
        public BeanWatch watch(BeanDefinitionWatcher<T> watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            return registry.watchDefinitions(beanType, combine(qualifiers), watcher, handlers.snapshot());
        }

        @Override
        public InstanceWatchRequest<T> instances() {
            return new InstanceRequest<>(registry, beanType, combine(qualifiers));
        }

        @Override
        public <A extends Annotation> MethodWatchRequest<A> methods(Class<A> annotationType) {
            return new MethodRequest<>(registry, annotationType, beanType, combine(qualifiers));
        }
    }

    static final class InstanceRequest<T> implements InstanceWatchRequest<T> {
        private final BeanWatchRegistry registry;
        private final Argument<T> beanType;
        @Nullable
        private final Qualifier<T> qualifier;
        private final ChangeHandlers<BeanRegistration<T>, BeanInstanceChange.Replacement<T>> handlers = new ChangeHandlers<>();

        InstanceRequest(BeanWatchRegistry registry, Argument<T> beanType, @Nullable Qualifier<T> qualifier) {
            this.registry = registry;
            this.beanType = beanType;
            this.qualifier = qualifier;
        }

        @Override
        public InstanceWatchRequest<T> onAdded(Consumer<? super BeanRegistration<T>> handler) {
            handlers.onAdded(handler);
            return this;
        }

        @Override
        public InstanceWatchRequest<T> onRemoved(Consumer<? super BeanRegistration<T>> handler) {
            handlers.onRemoved(handler);
            return this;
        }

        @Override
        public InstanceWatchRequest<T> onReplaced(Consumer<? super BeanInstanceChange.Replacement<T>> handler) {
            handlers.onReplaced(handler);
            return this;
        }

        @Override
        public BeanWatch watch() {
            requireHandler(handlers);
            return registry.watchInstances(beanType, qualifier, null, handlers.snapshot());
        }

        @Override
        public BeanWatch watch(BeanInstanceWatcher<T> watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            return registry.watchInstances(beanType, qualifier, watcher, handlers.snapshot());
        }
    }

    static final class MethodRequest<A extends Annotation> implements MethodWatchRequest<A> {
        private final BeanWatchRegistry registry;
        private final Class<A> annotationType;
        @Nullable
        private final Argument<?> beanType;
        @Nullable
        private final Qualifier<?> qualifier;
        private final ChangeHandlers<BeanExecutableMethod<A>, ExecutableMethodChange.Replacement<A>> handlers = new ChangeHandlers<>();

        MethodRequest(BeanWatchRegistry registry, Class<A> annotationType, @Nullable Argument<?> beanType, @Nullable Qualifier<?> qualifier) {
            this.registry = registry;
            this.annotationType = Objects.requireNonNull(annotationType, "annotationType");
            this.beanType = beanType;
            this.qualifier = qualifier;
        }

        @Override
        public MethodWatchRequest<A> onAdded(Consumer<? super BeanExecutableMethod<A>> handler) {
            handlers.onAdded(handler);
            return this;
        }

        @Override
        public MethodWatchRequest<A> onRemoved(Consumer<? super BeanExecutableMethod<A>> handler) {
            handlers.onRemoved(handler);
            return this;
        }

        @Override
        public MethodWatchRequest<A> onReplaced(Consumer<? super ExecutableMethodChange.Replacement<A>> handler) {
            handlers.onReplaced(handler);
            return this;
        }

        @Override
        public BeanWatch watch() {
            requireHandler(handlers);
            return registry.watchMethods(annotationType, beanType, qualifier, null, handlers.snapshot());
        }

        @Override
        public BeanWatch watch(ExecutableMethodWatcher<A> watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            return registry.watchMethods(annotationType, beanType, qualifier, watcher, handlers.snapshot());
        }
    }

    static final class ConfigurationRequest implements ConfigurationWatchRequest {
        private final BeanWatchRegistry registry;
        @Nullable
        private final String prefix;
        private boolean firstBatch;

        ConfigurationRequest(BeanWatchRegistry registry, @Nullable String prefix) {
            this.registry = registry;
            this.prefix = prefix;
        }

        @Override
        public ConfigurationWatchRequest withFirstBatch() {
            firstBatch = true;
            return this;
        }

        @Override
        public BeanWatch watch(ConfigurationWatcher watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            return registry.watchConfiguration(prefix, watcher, change -> {
                watcher.onChange(change);
                return ReloadingConfigurationWatcher.Outcome.APPLIED;
            }, firstBatch);
        }

        @Override
        public BeanWatch watchReloading(ReloadingConfigurationWatcher watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            return registry.watchConfiguration(prefix, watcher, watcher, firstBatch);
        }
    }

    static final class ResourceRequest implements ResourceWatchRequest {
        private final BeanWatchRegistry registry;
        private final ResourceKind kind;
        private final Set<String> globs = new LinkedHashSet<>();

        ResourceRequest(BeanWatchRegistry registry, ResourceKind kind) {
            this.registry = registry;
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        @Override
        public ResourceWatchRequest include(String... globs) {
            for (String glob : globs) {
                this.globs.add(Objects.requireNonNull(glob, "glob"));
            }
            return this;
        }

        @Override
        public BeanWatch watch(ResourceWatcher watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            return registry.watchResources(new ResourceSelector(kind, globs), watcher);
        }
    }

    static final class ClassChangeRequest implements ClassChangeWatchRequest {
        private final BeanWatchRegistry registry;
        private final BooleanSupplier developmentMode;

        ClassChangeRequest(BeanWatchRegistry registry, BooleanSupplier developmentMode) {
            this.registry = registry;
            this.developmentMode = developmentMode;
        }

        @Override
        public BeanWatch watch(ClassChangeWatcher watcher) {
            Objects.requireNonNull(watcher, WATCHER);
            if (!developmentMode.getAsBoolean()) {
                // classes change only under a development launcher: nothing to register, nothing to keep
                return BeanWatchRegistry.INACTIVE;
            }
            return registry.watchClassChanges(watcher);
        }
    }
}
