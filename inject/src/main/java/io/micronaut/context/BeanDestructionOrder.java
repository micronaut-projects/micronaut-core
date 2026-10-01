/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.proxy.InterceptedBeanProxy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Orders an existing set of scope-owned instances without resolving additional beans or taking ownership.
 * Dependencies outside the supplied set remain controlled by their scopes.
 * @since 5.3.0
 */
@Internal
public final class BeanDestructionOrder {
    private BeanDestructionOrder() {
    }

    private static void collectDependencyRegistrations(BeanRegistration<?> registration,
                                                        Set<BeanRegistration<?>> visited) {
        if (!visited.add(registration)) {
            return;
        }
        if (registration instanceof DependentBeanProvider provider) {
            for (BeanRegistration<?> owned : provider.dependentBeans()) {
                collectDependencyRegistrations(owned, visited);
            }
        }
        if (registration instanceof BeanDisposingRegistration<?> disposing) {
            // Shared registrations remain owned by their scopes and form leaves of this consumer's tree.
            visited.addAll(disposing.getDependencies().requiredBeans());
        }
        if (registration.getBean() instanceof InterceptedBeanProxy<?> proxy && proxy.hasCachedInterceptedTarget()) {
            BeanRegistration<?> target = proxy.interceptedTargetRegistration();
            if (target != null) {
                collectDependencyRegistrations(target, visited);
            }
            if (registration instanceof BeanDisposingRegistration<?> disposing && disposing.getProxyTargetContext() != null) {
                for (BeanRegistration<?> owned : disposing.getProxyTargetContext().getCachedProxyTargetDependents()) {
                    collectDependencyRegistrations(owned, visited);
                }
            }
        }
    }

    /**
     * Sorts registrations into the order in which they should be destroyed.
     *
     * <p>A bean is destroyed before every bean it requires, whether the requirement comes from an injection point or
     * from {@link io.micronaut.context.annotation.DependsOn}, or from dynamically resolved dependencies, so that a dependency outlives its dependents.
     * Where the dependencies leave the order open, destruction listeners are kept last and beans are destroyed in
     * bean name order so that the sequence is stable between runs. A dependency cycle
     * cannot satisfy that guarantee for every one of its members, so it is broken by destroying the first bean in
     * name order that lies on a cycle; beans that merely depend on a cycle are never chosen to break it.</p>
     *
     * @param beans The registrations
     * @param <T> The registration type
     * @return The registrations in destruction order
     */
    public static <T extends CreatedBean<?>> List<T> sort(Collection<T> beans) {
        final int size = beans.size();
        // Node indexes provide a stable tie-breaker for the destruction sequence.
        final List<T> nodes = new ArrayList<>(beans);
        // Keep destruction listeners alive as long as possible, but respect their own dependencies too.
        nodes.sort(Comparator.<T, Boolean>comparing(registration ->
                registration.bean() instanceof BeanPreDestroyEventListener || registration.bean() instanceof BeanDestroyedEventListener)
            .thenComparing(registration -> registration.definition().getName()));

        final Map<Object, List<Integer>> nodesByInstance = new IdentityHashMap<>(size);
        for (int i = 0; i < size; i++) {
            nodesByInstance.computeIfAbsent(nodes.get(i).bean(), bean -> new ArrayList<>(1)).add(i);
        }
        final Map<Class<?>, List<Integer>> nodesByType = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            nodesByType.computeIfAbsent(nodes.get(i).definition().getBeanType(), type -> new ArrayList<>(1)).add(i);
        }

        // qualifiers are not taken into account, so every singleton assignable to a required type is a candidate
        final Map<Class<?>, List<Integer>> candidatesByRequiredType = new HashMap<>();
        // dependencies[i] holds the nodes that node i requires; each of them must be destroyed after node i
        final List<List<Integer>> dependencies = new ArrayList<>(size);
        final int[] dependents = new int[size];
        for (int i = 0; i < size; i++) {
            Set<BeanRegistration<?>> resolved = Collections.newSetFromMap(new IdentityHashMap<>());
            if (nodes.get(i) instanceof BeanRegistration<?> registration) {
                collectDependencyRegistrations(registration, resolved);
            }
            final Set<Class<?>> required = new HashSet<>(nodes.get(i).definition().getRequiredComponents());
            final List<Integer> nodeDependencies = new ArrayList<>();
            for (BeanRegistration<?> dependency : resolved) {
                List<Integer> matches = nodesByInstance.get(dependency.getBean());
                if (matches != null) {
                    for (int j : matches) {
                        if (j != i && !nodeDependencies.contains(j)) {
                            nodeDependencies.add(j);
                            dependents[j]++;
                        }
                    }
                } else {
                    // An owned prototype's injected dependencies must also outlive the owning singleton.
                    required.addAll(dependency.getBeanDefinition().getRequiredComponents());
                }
            }
            for (Class<?> requiredType : required) {
                final List<Integer> candidates = candidatesByRequiredType.computeIfAbsent(requiredType, type -> {
                    final List<Integer> assignable = new ArrayList<>();
                    for (Map.Entry<Class<?>, List<Integer>> entry : nodesByType.entrySet()) {
                        if (type.isAssignableFrom(entry.getKey())) {
                            assignable.addAll(entry.getValue());
                        }
                    }
                    return assignable;
                });
                for (int j : candidates) {
                    if (j != i && !nodeDependencies.contains(j)) {
                        nodeDependencies.add(j);
                        dependents[j]++;
                    }
                }
            }
            dependencies.add(nodeDependencies);
        }

        // A listener must survive every bean whose destruction it observes, including beans in a dependency
        // cycle. A queue tie-breaker alone lets a ready listener be destroyed before such a cycle is broken.
        final Map<Class<?>, List<Integer>> listenersByObservedType = new HashMap<>();
        for (int i = 0; i < size; i++) {
            T node = nodes.get(i);
            if (node.bean() instanceof BeanPreDestroyEventListener) {
                addListener(listenersByObservedType, node.definition(), BeanPreDestroyEventListener.class, i);
            }
            if (node.bean() instanceof BeanDestroyedEventListener) {
                addListener(listenersByObservedType, node.definition(), BeanDestroyedEventListener.class, i);
            }
        }
        for (Map.Entry<Class<?>, List<Integer>> entry : listenersByObservedType.entrySet()) {
            for (int i = 0; i < size; i++) {
                if (entry.getKey().isAssignableFrom(nodes.get(i).definition().getBeanType())) {
                    List<Integer> nodeDependencies = dependencies.get(i);
                    for (int listener : entry.getValue()) {
                        if (listener != i && !nodeDependencies.contains(listener)) {
                            nodeDependencies.add(listener);
                            dependents[listener]++;
                        }
                    }
                }
            }
        }

        // Kahn's algorithm: repeatedly destroy the first bean that no remaining bean requires
        final List<T> sorted = new ArrayList<>(size);
        final boolean[] destroyed = new boolean[size];
        final PriorityQueue<Integer> ready = new PriorityQueue<>(Math.max(1, size));
        for (int i = 0; i < size; i++) {
            if (dependents[i] == 0) {
                ready.add(i);
            }
        }
        while (sorted.size() < size) {
            if (ready.isEmpty()) {
                // the remaining beans are a cycle plus whatever the cycle requires, so break the cycle itself
                ready.add(findCycleNode(dependencies, destroyed));
            }
            final int i = ready.poll();
            if (destroyed[i]) {
                continue;
            }
            destroyed[i] = true;
            sorted.add(nodes.get(i));
            for (int j : dependencies.get(i)) {
                if (--dependents[j] == 0 && !destroyed[j]) {
                    ready.add(j);
                }
            }
        }
        return sorted;
    }

    private static void addListener(Map<Class<?>, List<Integer>> listenersByObservedType,
                                    BeanDefinition<?> definition,
                                    Class<?> listenerType,
                                    int index) {
        List<Argument<?>> arguments = definition.getTypeArguments(listenerType);
        Class<?> observedType = arguments.isEmpty() ? Object.class : arguments.getLast().getType();
        listenersByObservedType.computeIfAbsent(observedType, type -> new ArrayList<>(1)).add(index);
    }

    /**
     * Finds the first not yet destroyed node in index order that lies on a dependency cycle of the remaining graph.
     *
     * @param dependencies The dependencies of every node
     * @param destroyed    The nodes that have already been sorted
     * @return The index of the node to break the cycle at
     */
    private static int findCycleNode(List<List<Integer>> dependencies, boolean[] destroyed) {
        final boolean[] visited = new boolean[destroyed.length];
        final Deque<Integer> stack = new ArrayDeque<>();
        for (int candidate = 0; candidate < destroyed.length; candidate++) {
            if (destroyed[candidate]) {
                continue;
            }
            // the candidate is on a cycle when it can reach itself along the dependencies of the remaining nodes
            Arrays.fill(visited, false);
            stack.clear();
            stack.push(candidate);
            while (!stack.isEmpty()) {
                for (int next : dependencies.get(stack.pop())) {
                    if (next == candidate) {
                        return candidate;
                    }
                    if (!destroyed[next] && !visited[next]) {
                        visited[next] = true;
                        stack.push(next);
                    }
                }
            }
        }
        throw new IllegalStateException("No dependency cycle found among the remaining beans");
    }

}
