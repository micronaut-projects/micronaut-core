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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link BeanDependencyGraph} a {@link DefaultBeanContext} records.
 *
 * <p>Definitions are keyed by their class and declared qualifier, the identity the singleton scope
 * uses, so the delegate a factory produces for each qualifier is one node per qualifier.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class DefaultBeanDependencyGraph implements BeanDependencyGraph {

    private final Map<Key, Set<BeanDependency>> byDependent = new ConcurrentHashMap<>();
    private final Map<Key, Set<BeanDependency>> byDependency = new ConcurrentHashMap<>();
    /**
     * How many live instances of a non-singleton dependent recorded an edge, since the instances of a
     * prototype share its definition: the edge goes when the last of them is destroyed.
     */
    private final Map<BeanDependency, AtomicInteger> instances = new ConcurrentHashMap<>();

    /**
     * Records that the bean the given resolution context is creating received the given bean at the
     * context's current injection point.
     *
     * @param resolutionContext The resolution context of the receiving bean, or null outside a resolution
     * @param dependency The received bean's definition
     */
    @SuppressWarnings("unchecked")
    void record(@Nullable BeanResolutionContext resolutionContext, BeanDefinition<?> dependency) {
        if (resolutionContext == null) {
            return;
        }
        BeanResolutionContext.Segment<?, ?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        if (segment == null) {
            return;
        }
        BeanDefinition<?> dependent = segment.getDeclaringType();
        if (dependent == null || dependent == dependency) {
            return;
        }
        // a segment is pushed with the target definition while the bean being created may be one member of an
        // @EachBean or @EachProperty set: the qualifier the context resolves it under tells the members apart
        Qualifier<?> dependentQualifier = segment.getDeclaringTypeQualifier();
        if (dependentQualifier != null && !(dependent instanceof BeanDefinitionDelegate<?>) && !(dependent instanceof RuntimeBeanDefinition<?>)) {
            dependent = BeanDefinitionDelegate.create((BeanDefinition<Object>) dependent, (Qualifier<Object>) dependentQualifier);
        }
        Argument<?> argument = segment.getArgument();
        BeanDependency edge = new BeanDependency(
            dependent,
            dependency,
            kindOf(segment),
            argument != null && argument.isProvider(),
            argument != null && (argument.isContainerType() || argument.getType().isArray())
        );
        byDependent.computeIfAbsent(Key.of(dependent), k -> ConcurrentHashMap.newKeySet()).add(edge);
        byDependency.computeIfAbsent(Key.of(dependency), k -> ConcurrentHashMap.newKeySet()).add(edge);
        if (!dependent.isSingleton()) {
            instances.computeIfAbsent(edge, e -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static InjectionKind kindOf(BeanResolutionContext.Segment<?, ?> segment) {
        // the segments are their own injection points; the constructor segment covers factory methods too
        if (segment instanceof AbstractBeanResolutionContext.ConstructorSegment) {
            return InjectionKind.CONSTRUCTOR;
        }
        if (segment instanceof AbstractBeanResolutionContext.FieldSegment<?, ?>) {
            return InjectionKind.FIELD;
        }
        if (segment instanceof AbstractBeanResolutionContext.MethodSegment<?, ?>) {
            // an argument of a factory method is held by the produced bean as a constructor argument is
            if (segment instanceof ArgumentInjectionPoint<?, ?> argumentInjectionPoint
                && argumentInjectionPoint.getOuterInjectionPoint() instanceof ConstructorInjectionPoint<?>) {
                return InjectionKind.CONSTRUCTOR;
            }
            return InjectionKind.METHOD;
        }
        InjectionPoint<?> injectionPoint = segment.getInjectionPoint();
        if (injectionPoint instanceof ConstructorInjectionPoint<?>) {
            return InjectionKind.CONSTRUCTOR;
        }
        if (injectionPoint instanceof FieldInjectionPoint<?, ?>) {
            return InjectionKind.FIELD;
        }
        if (injectionPoint instanceof MethodInjectionPoint<?, ?>) {
            return InjectionKind.METHOD;
        }
        return InjectionKind.OTHER;
    }

    @Override
    public Collection<BeanDependency> dependenciesOf(BeanDefinition<?> dependent) {
        return List.copyOf(byDependent.getOrDefault(Key.of(dependent), Set.of()));
    }

    @Override
    public Collection<BeanDependency> dependentsOf(BeanDefinition<?> dependency) {
        return List.copyOf(byDependency.getOrDefault(Key.of(dependency), Set.of()));
    }

    @Override
    public Set<BeanDefinition<?>> transitiveDependentsOf(BeanDefinition<?> dependency) {
        Set<Key> visited = new LinkedHashSet<>();
        Set<BeanDefinition<?>> result = new LinkedHashSet<>();
        Deque<BeanDefinition<?>> queue = new ArrayDeque<>();
        queue.add(dependency);
        visited.add(Key.of(dependency));
        while (!queue.isEmpty()) {
            BeanDefinition<?> current = queue.poll();
            for (BeanDependency edge : byDependency.getOrDefault(Key.of(current), Set.of())) {
                if (edge.lazy()) {
                    continue;
                }
                if (visited.add(Key.of(edge.dependent()))) {
                    result.add(edge.dependent());
                    queue.add(edge.dependent());
                }
            }
        }
        return result;
    }

    @Override
    public Set<BeanDefinition<?>> transitiveDependenciesOf(BeanDefinition<?> dependent) {
        Set<Key> visited = new LinkedHashSet<>();
        Set<BeanDefinition<?>> result = new LinkedHashSet<>();
        Deque<BeanDefinition<?>> queue = new ArrayDeque<>();
        queue.add(dependent);
        visited.add(Key.of(dependent));
        while (!queue.isEmpty()) {
            BeanDefinition<?> current = queue.poll();
            for (BeanDependency edge : byDependent.getOrDefault(Key.of(current), Set.of())) {
                if (edge.lazy()) {
                    continue;
                }
                if (visited.add(Key.of(edge.dependency()))) {
                    result.add(edge.dependency());
                    queue.add(edge.dependency());
                }
            }
        }
        return result;
    }

    @Override
    public Collection<BeanDependency> dependencies() {
        List<BeanDependency> all = new ArrayList<>();
        for (Set<BeanDependency> edges : byDependent.values()) {
            all.addAll(edges);
        }
        return all;
    }

    /**
     * Forgets what the given bean received, because the bean was destroyed. What received the bean
     * stays recorded until that is destroyed in turn.
     *
     * @param dependent The destroyed bean's definition
     */
    void remove(BeanDefinition<?> dependent) {
        Key key = Key.of(dependent);
        Set<BeanDependency> edges = byDependent.get(key);
        if (edges == null) {
            return;
        }
        for (BeanDependency edge : List.copyOf(edges)) {
            if (!dependent.isSingleton()) {
                // one instance of several gone: the others still hold what they received
                AtomicInteger count = instances.get(edge);
                if (count != null && count.decrementAndGet() > 0) {
                    continue;
                }
                instances.remove(edge);
            }
            edges.remove(edge);
            Key dependencyKey = Key.of(edge.dependency());
            Set<BeanDependency> dependents = byDependency.get(dependencyKey);
            if (dependents != null) {
                dependents.remove(edge);
                if (dependents.isEmpty()) {
                    byDependency.remove(dependencyKey, dependents);
                }
            }
        }
        if (edges.isEmpty()) {
            byDependent.remove(key, edges);
        }
    }

    /**
     * Forgets the field and method injections of a bean that is about to be injected again, so that
     * the edges recorded by the new injection replace, rather than join, the old ones.
     *
     * @param dependent The bean's definition
     */
    void removeReinjectable(BeanDefinition<?> dependent) {
        Key key = Key.of(dependent);
        Set<BeanDependency> edges = byDependent.get(key);
        if (edges == null) {
            return;
        }
        for (BeanDependency edge : List.copyOf(edges)) {
            if (edge.reinjectable()) {
                edges.remove(edge);
                instances.remove(edge);
                Key dependencyKey = Key.of(edge.dependency());
                Set<BeanDependency> dependents = byDependency.get(dependencyKey);
                if (dependents != null) {
                    dependents.remove(edge);
                }
            }
        }
    }

    /**
     * Forgets every recorded dependency.
     */
    void clear() {
        byDependent.clear();
        byDependency.clear();
        instances.clear();
    }

    private record Key(Class<?> definitionClass, @Nullable Object qualifier, @Nullable Class<?> beanType) {
        static Key of(BeanDefinition<?> definition) {
            if (definition instanceof BeanDefinitionDelegate<?> delegate) {
                return new Key(delegate.getDelegate().getClass(), delegate.getDeclaredQualifier(), delegate.getBeanType());
            }
            if (definition instanceof RuntimeBeanDefinition<?> runtime) {
                return new Key(runtime.getClass(), runtime.getDeclaredQualifier(), runtime.getBeanType());
            }
            return new Key(definition.getClass(), null, null);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key key
                && definitionClass == key.definitionClass
                && Objects.equals(qualifier, key.qualifier)
                && beanType == key.beanType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(definitionClass, qualifier, beanType);
        }
    }
}
