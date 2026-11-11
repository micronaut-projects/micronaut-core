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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
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
 * {@link #isStaleType(Class)} holds. Registries built from beans (routes, scheduled jobs, message
 * consumers) do not listen here; they watch the beans they are built from and receive the change
 * as a batch. Listeners that need the exact classes look at {@link #changes()}.</p>
 *
 * <p>The launcher publishes the event in a {@link ReloadStrategy#RESTART restart} as well, to the
 * context being stopped, so that beans carried over to the next context can clean up what they cached
 * about the retired classes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class ClassChangeEvent extends ApplicationEvent {

    private final transient Set<ClassLoader> retiredLoaders;
    private final transient ClassLoader newLoader;
    private final transient List<ClassChange> changes;
    private final transient Set<String> changedClassNames;
    private final transient Set<String> changedTypeNames;
    private final ReloadStrategy strategy;

    /**
     * Creates the event.
     *
     * @param source The launcher or component publishing the event
     * @param retiredLoaders The classloaders of the generations retired by this change; a class loaded by one of them is stale
     * @param newLoader The classloader of the new generation
     * @param changes The changed classes
     * @param strategy The strategy the launcher applies
     */
    public ClassChangeEvent(Object source,
                            Set<ClassLoader> retiredLoaders,
                            ClassLoader newLoader,
                            List<ClassChange> changes,
                            ReloadStrategy strategy) {
        super(source);
        Objects.requireNonNull(retiredLoaders, "retiredLoaders");
        Set<ClassLoader> identity = Collections.newSetFromMap(new IdentityHashMap<>());
        identity.addAll(retiredLoaders);
        this.retiredLoaders = Collections.unmodifiableSet(identity);
        this.newLoader = Objects.requireNonNull(newLoader, "newLoader");
        this.changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        Set<String> names = new HashSet<>();
        Set<String> typeNames = new HashSet<>();
        for (ClassChange change : this.changes) {
            String name = change.className();
            names.add(name);
            typeNames.add(name);
            // the code of a nested, inner, local or anonymous class runs as part of the classes enclosing it
            int simpleName = name.lastIndexOf('.') + 1;
            for (int i = name.lastIndexOf('$'); i > simpleName; i = name.lastIndexOf('$', i - 1)) {
                typeNames.add(name.substring(0, i));
            }
        }
        this.changedClassNames = Collections.unmodifiableSet(names);
        this.changedTypeNames = Collections.unmodifiableSet(typeNames);
        this.strategy = Objects.requireNonNull(strategy, "strategy");
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
     * @return The strategy applied
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
    public boolean isStaleType(@Nullable Class<?> type) {
        return type != null && type.getClassLoader() != null && retiredLoaders.contains(type.getClassLoader());
    }

    /**
     * Whether a bean definition belongs to a retired generation: its own class or its bean type is stale.
     *
     * @param definition The definition
     * @return True if the definition must no longer be used
     */
    public boolean isStaleDefinition(@Nullable BeanDefinition<?> definition) {
        if (definition == null) {
            return false;
        }
        if (isStaleType(definition.getClass()) || isStaleType(definition.getBeanType())) {
            return true;
        }
        // a factory-produced bean's definition class is generated next to the factory, whose class may be stale
        // while the produced type is a library type
        if (definition.getDeclaringType().filter(this::isStaleType).isPresent()) {
            return true;
        }
        // a delegate (an @EachBean registration, a qualified factory bean) is a framework class wrapping the
        // generated definition; a proxy definition names the definition it targets
        if (definition instanceof DelegatingBeanDefinition<?> delegating && delegating.getTarget() != definition) {
            return isStaleDefinition(delegating.getTarget());
        }
        return definition instanceof ProxyBeanDefinition<?> proxy && (isStaleType(proxy.getTargetDefinitionType()) || isStaleType(proxy.getTargetType()));
    }

    /**
     * Whether a method's declaring type is stale.
     *
     * @param method The method
     * @return True if the method must no longer be invoked
     */
    public boolean isStaleMethod(@Nullable ExecutableMethod<?, ?> method) {
        return method != null && isStaleType(method.getDeclaringType());
    }

    /**
     * Whether an object is an instance of a stale class.
     *
     * @param instance The instance
     * @return True if the instance belongs to a retired generation
     */
    public boolean isStaleInstance(@Nullable Object instance) {
        return instance != null && isStaleType(instance.getClass());
    }

    /**
     * Whether the class of the given name is in the change set.
     *
     * @param className The binary name
     * @return True if the class was added, modified or removed
     */
    public boolean affects(String className) {
        return changedClassNames.contains(className);
    }

    /**
     * Whether a bean definition is built from a class in the change set: the generated definition itself, the bean
     * type or one of its supertypes, the factory that declares the definition of a factory method or field, or the
     * class an advised or proxy definition intercepts. The classes are matched by name, so that the answer holds for
     * a change that retires a classloader too, where the definitions of the context are those of the retired
     * generation.
     *
     * <p>A changed nested, inner, local or anonymous class counts as a change of every class enclosing it, by its
     * binary name: an edit of the body of {@code Foo$1} changes what a bean of type {@code Foo} runs, though
     * {@code Foo} itself compiles to the same bytes. A static nested bean therefore also selects the definitions
     * of its enclosing classes, which errs on the side of doing too much. A changed enclosing class does not count
     * as a change of the classes nested in it.</p>
     *
     * <p>A change applied in place, which retires no classloader, leaves the definitions of the context as they were,
     * while the classes it names run new code: a bean whose definition this method selects runs code of the change,
     * and was built by it. A module that keeps state built from some beans, such as message listeners, tests the
     * definitions it selects, for example those of {@link io.micronaut.context.BeanContext#getBeanDefinitions(Class,
     * io.micronaut.context.Qualifier)}, rather than matching the names of generated classes.</p>
     *
     * @param definition The definition
     * @return True if a class it is built from is in the change set
     */
    public boolean affectsDefinition(@Nullable BeanDefinition<?> definition) {
        if (definition == null || changedClassNames.isEmpty()) {
            return false;
        }
        if (changedClassNames.contains(definition.getClass().getName()) || affectsHierarchy(definition.getBeanType())) {
            return true;
        }
        // a factory method or field: the product's definition names the factory, whose code builds it
        if (definition.getDeclaringType().filter(this::affectsHierarchy).isPresent()) {
            return true;
        }
        // introduction advice on an interface, or around advice on a class: the bean type is the generated proxy
        if (definition instanceof AdvisedBeanType<?> advised && affectsHierarchy(advised.getInterceptedType())) {
            return true;
        }
        // a delegate (an @EachBean registration, a qualified factory bean) wraps the generated definition
        if (definition instanceof DelegatingBeanDefinition<?> delegating && delegating.getTarget() != definition) {
            return affectsDefinition(delegating.getTarget());
        }
        return definition instanceof ProxyBeanDefinition<?> proxy
            && (changedClassNames.contains(proxy.getTargetDefinitionType().getName()) || affectsHierarchy(proxy.getTargetType()));
    }

    /**
     * Whether the class or one of its supertypes is in the change set, or encloses a class in it: a superclass, or an
     * interface whose default methods the class runs.
     */
    private boolean affectsHierarchy(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        for (Class<?> supertype : ClassUtils.resolveHierarchy(type)) {
            if (changedTypeNames.contains(supertype.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ClassChangeEvent{strategy=" + strategy + ", changes=" + changes.size() + '}';
    }
}
