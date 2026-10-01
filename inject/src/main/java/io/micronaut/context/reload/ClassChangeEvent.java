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
package io.micronaut.context.reload;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The application's classes changed: a development launcher compiled them into a new generation
 * of the application classloader and is about to replace the beans of the retired generation.
 *
 * <p>The event is published before any bean is touched. Listeners use it to forget cached state
 * about classes of the retired generation: a cache keyed by class evicts every key for which
 * {@link #isStale(Class)} holds. Registries built from beans (routes, scheduled jobs, message
 * consumers) do not listen here; they watch the beans they are built from and receive the change
 * as a batch. Listeners that need the exact classes look at {@link #changes()}.</p>
 *
 * <p>The launcher publishes the event in a {@link ReloadStrategy#RESTART restart} as well, to the
 * context being stopped, so that beans a {@link BeanRetentionPolicy} carries over can clean up
 * what they cached about the retired classes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ClassChangeEvent extends ApplicationEvent {

    private final int generation;
    private final Set<ClassLoader> retiredLoaders;
    private final ClassLoader newLoader;
    private final List<ClassChange> changes;
    private final ReloadStrategy strategy;

    /**
     * Creates the event.
     *
     * @param source The launcher or component publishing the event
     * @param generation The number of the generation being retired, counted from one
     * @param retiredLoaders The classloaders of the generations retired by this change; a class loaded by one of them is stale
     * @param newLoader The classloader of the new generation
     * @param changes The changed classes
     * @param strategy The strategy the launcher applies, {@link ReloadStrategy#RESTART} or {@link ReloadStrategy#RELOAD}
     */
    public ClassChangeEvent(Object source,
                            int generation,
                            Set<ClassLoader> retiredLoaders,
                            ClassLoader newLoader,
                            List<ClassChange> changes,
                            ReloadStrategy strategy) {
        super(source);
        Objects.requireNonNull(retiredLoaders, "retiredLoaders");
        Set<ClassLoader> identity = Collections.newSetFromMap(new IdentityHashMap<>());
        identity.addAll(retiredLoaders);
        this.generation = generation;
        this.retiredLoaders = Collections.unmodifiableSet(identity);
        this.newLoader = Objects.requireNonNull(newLoader, "newLoader");
        this.changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        if (strategy == ReloadStrategy.AUTO) {
            throw new IllegalArgumentException("The strategy of a change is the one applied, RESTART or RELOAD, not AUTO");
        }
    }

    /**
     * @return The number of the generation being retired, counted from one
     */
    public int generation() {
        return generation;
    }

    /**
     * @return The classloaders of the retired generations
     */
    public Set<ClassLoader> retiredLoaders() {
        return retiredLoaders;
    }

    /**
     * @return The classloader of the new generation
     */
    public ClassLoader newLoader() {
        return newLoader;
    }

    /**
     * @return The changed classes
     */
    public List<ClassChange> changes() {
        return changes;
    }

    /**
     * @return The strategy applied: {@link ReloadStrategy#RESTART} or {@link ReloadStrategy#RELOAD}
     */
    public ReloadStrategy strategy() {
        return strategy;
    }

    /**
     * Whether a class was loaded by a retired generation. Every class of the reloadable tier is
     * stale after a change, whether or not its own source changed, because it links against the
     * retired generation.
     *
     * @param type The class
     * @return True if the class must no longer be used
     */
    public boolean isStale(@Nullable Class<?> type) {
        return type != null && type.getClassLoader() != null && retiredLoaders.contains(type.getClassLoader());
    }

    /**
     * Whether a bean definition belongs to a retired generation: its own class or its bean type is stale.
     *
     * @param definition The definition
     * @return True if the definition must no longer be used
     */
    public boolean isStale(@Nullable BeanDefinition<?> definition) {
        if (definition == null) {
            return false;
        }
        if (isStale(definition.getClass()) || isStale(definition.getBeanType())) {
            return true;
        }
        // a factory-produced bean's definition class is generated next to the factory, whose class may be stale
        // while the produced type is a library type
        if (definition.getDeclaringType().filter(this::isStale).isPresent()) {
            return true;
        }
        // a delegate (an @EachBean registration, a qualified factory bean) is a framework class wrapping the
        // generated definition; a proxy definition names the definition it targets
        if (definition instanceof DelegatingBeanDefinition<?> delegating && delegating.getTarget() != definition) {
            return isStale(delegating.getTarget());
        }
        return definition instanceof ProxyBeanDefinition<?> proxy && (isStale(proxy.getTargetDefinitionType()) || isStale(proxy.getTargetType()));
    }

    /**
     * Whether a method's declaring type is stale.
     *
     * @param method The method
     * @return True if the method must no longer be invoked
     */
    public boolean isStale(@Nullable ExecutableMethod<?, ?> method) {
        return method != null && isStale(method.getDeclaringType());
    }

    /**
     * Whether an object is an instance of a stale class.
     *
     * @param instance The instance
     * @return True if the instance belongs to a retired generation
     */
    public boolean isStaleInstance(@Nullable Object instance) {
        return instance != null && isStale(instance.getClass());
    }

    /**
     * Whether the class of the given name is in the change set.
     *
     * @param className The binary name
     * @return True if the class was added, modified or removed
     */
    public boolean affects(String className) {
        for (ClassChange change : changes) {
            if (change.className().equals(className)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ClassChangeEvent{generation=" + generation + ", strategy=" + strategy + ", changes=" + changes.size() + '}';
    }
}
