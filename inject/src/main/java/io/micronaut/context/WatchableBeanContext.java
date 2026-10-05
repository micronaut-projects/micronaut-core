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

import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.BeanWatcher;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.context.watch.ResourceSelector;
import io.micronaut.context.watch.ResourceWatcher;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * A bean context whose definitions, beans, methods, configuration, resources and classes can be watched: a
 * watcher registers for the specific things it derives state from and receives one batched change
 * whenever they change, the startup state being the first batch.
 *
 * <p>Implemented by the default context, which is injectable as this type. A watch registered while a
 * bean is being created belongs to that bean and is closed when the bean is destroyed.</p>
 *
 * <p>This is public API for modules, and experimental: a module that derives state from the context,
 * such as a registry built from beans or a cache of what configuration says, watches it here rather
 * than listening for events, so that it follows a development reload, a definition registered at
 * runtime and a configuration refresh alike. Being experimental, it may still change in a minor
 * release. The context is the only implementation; a module tests for it with {@code instanceof}
 * and watches nothing when the context it is given is another one.</p>
 *
 * <h2>Reading and watching at once</h2>
 * <p>A bean that reads what it derives state from and then registers a watch can miss a change made in
 * between. The first batch closes that gap: it is read after the watch is registered, under the watch's
 * own delivery lock, so a bean that builds its state from the first batch, rather than from a read of
 * its own, misses nothing. The guarantees, for every watch:</p>
 * <ul>
 * <li>Batches reach a watcher one at a time, the first batch first, then the changes in the order they
 * were numbered as their delivery began; none is delivered before the first batch, nor concurrently with
 * another. No lock is held while a watcher runs: a change made by a watcher, to a watch another thread is
 * delivering to, is delivered by that thread after the batch it is on, and a configuration watch's answer to
 * it is acted on there rather than returned to the refresh.</li>
 * <li>No change is lost: one applied after the first batch was read is delivered after it.</li>
 * <li>A change applied before the first batch began to be read shows in it, and is not delivered again.
 * Only a change applied while the first batch was being read can show in it and also be delivered
 * after it; a watcher treats an addition it already has, or a removal of something it never had, as
 * nothing to do. Resource watches know their state exactly and never see such a repeat.</li>
 * </ul>
 * <p>Definition, bean, method and resource watches always have a first batch. A configuration watch has
 * one when registered with {@link #watchConfiguration(String, ConfigurationWatcher, boolean)}, which calls
 * the watcher once with {@link io.micronaut.context.watch.ConfigurationChange#ofInitial()} to read the
 * configuration as it is. A class change watch has none: nothing has changed when it is registered.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public sealed interface WatchableBeanContext extends BeanContext permits DefaultBeanContext {

    /**
     * Watches the bean definitions of a type. The watcher receives the definitions present as its first
     * batch, at startup or at once when the context is already running, then one batch per change: a
     * definition registered at runtime, or the definitions a development reload retires and adds.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch, to close when the watcher no longer needs changes
     */
    <T> BeanWatch watchDefinitions(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanDefinitionWatcher<T> watcher);

    /**
     * Watches the bean definitions of a type.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch
     * @see #watchDefinitions(Argument, Qualifier, BeanDefinitionWatcher)
     */
    default <T> BeanWatch watchDefinitions(Class<T> beanType, @Nullable Qualifier<T> qualifier, BeanDefinitionWatcher<T> watcher) {
        return watchDefinitions(Argument.of(beanType), qualifier, watcher);
    }

    /**
     * Watches the beans of a type, creating them. The watcher receives the beans present as its first
     * batch, then one batch per change, with the instances that went and the ones that came. The
     * instances delivered stay the ones delivered: a prototype among the candidates is created once for
     * the watch, not again for every batch.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch
     */
    <T> BeanWatch watchBeans(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanWatcher<T> watcher);

    /**
     * Watches the beans of a type, creating them.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, or null for any
     * @param watcher The watcher
     * @param <T> The bean type
     * @return The watch
     * @see #watchBeans(Argument, Qualifier, BeanWatcher)
     */
    default <T> BeanWatch watchBeans(Class<T> beanType, @Nullable Qualifier<T> qualifier, BeanWatcher<T> watcher) {
        return watchBeans(Argument.of(beanType), qualifier, watcher);
    }

    /**
     * Watches the executable methods carrying an annotation: the reload-aware form of an
     * {@link io.micronaut.context.processor.ExecutableMethodProcessor}. The watcher receives the methods
     * present as its first batch, then one batch per change.
     *
     * @param annotationType The annotation the methods carry, directly or as a stereotype
     * @param watcher The watcher
     * @param <A> The annotation type
     * @return The watch
     */
    <A extends Annotation> BeanWatch watchMethods(Class<A> annotationType, ExecutableMethodWatcher<A> watcher);

    /**
     * Watches the configuration under a prefix. The watcher is called, after the configuration beans
     * under the prefix were rebound, for every refresh that touches the prefix, and says what it did.
     * A watch registered while a bean is being created belongs to that bean, which is what lets the
     * watcher answer {@link ConfigurationWatcher.Outcome#RECREATE}.
     *
     * @param prefix The prefix, such as {@code datasources.default}
     * @param watcher The watcher
     * @return The watch
     */
    default BeanWatch watchConfiguration(String prefix, ConfigurationWatcher watcher) {
        return watchConfiguration(prefix, watcher, false);
    }

    /**
     * Watches the configuration under a prefix, optionally starting with a first batch. With
     * {@code initial}, the watcher is called once at registration, at startup when the context has not
     * started yet, with {@link io.micronaut.context.watch.ConfigurationChange#ofInitial()}: it reads the
     * configuration as it is then, and every refresh after that read reaches it, which a read of its own
     * before registering cannot promise. What the watcher answers to the first batch is not acted on: its
     * bean, if it has one, is still being created. Without {@code initial} this is
     * {@link #watchConfiguration(String, ConfigurationWatcher)}.
     *
     * @param prefix The prefix, such as {@code datasources.default}
     * @param watcher The watcher
     * @param initial Whether the watcher is first called with the configuration as it is
     * @return The watch
     */
    BeanWatch watchConfiguration(String prefix, ConfigurationWatcher watcher, boolean initial);

    /**
     * Watches the resources a selector selects: the files of a kind of resource root, by glob. The
     * watcher receives what is under the roots as its first batch, then one batch per change.
     *
     * @param selector The selector
     * @param watcher The watcher
     * @return The watch
     */
    BeanWatch watchResources(ResourceSelector selector, ResourceWatcher watcher);

    /**
     * Watches the class changes of a development reload: the watch for a cache keyed by class, which
     * evicts what {@link io.micronaut.context.reload.ClassChangeEvent#isStale(Class)} says belongs to a
     * retired generation. The watcher is called with each {@link io.micronaut.context.reload.ClassChangeEvent}
     * the launcher publishes, before the listeners of the event, and has no startup batch.
     *
     * <p>Classes change only in {@link io.micronaut.context.env.DevelopmentMode development mode}. In a
     * context that is not in development mode nothing is registered: the watch returned is already
     * inactive, and the watcher is never called nor kept.</p>
     *
     * @param watcher The watcher
     * @return The watch
     */
    BeanWatch watchClassChanges(ClassChangeWatcher watcher);

    /**
     * Recreates a singleton the context holds, and the beans that depend on it: the transitive dependents the
     * {@link BeanDependencyGraph} records are destroyed first, the bean is destroyed and created again from its
     * definition, and the dependents are created again when they are next requested, on top of the new instance. This
     * is what a watcher does when a change it is told about cannot be applied to a bean in place, such as a cache of
     * serializers or validators built from classes that a development reload replaced.
     *
     * <p>Meant for development-time watches. The dependents are known only when the context
     * {@link BeanContextConfiguration#isTrackBeanDependencies() tracks bean dependencies}, which by default only a
     * context in {@link io.micronaut.context.env.DevelopmentMode development mode} does; any other context recreates
     * nothing and returns false, rather than leave dependents holding the instance it destroyed.</p>
     *
     * @param bean The bean to recreate
     * @return Whether the context held the bean as a singleton and recreated it; false for a prototype, a bean of a
     * custom scope, a singleton registered at runtime, whose definition may only hand back the same instance, an
     * object the context does not hold, or a context that does not track bean dependencies
     * @since 5.3.0
     */
    boolean recreate(Object bean);

    /**
     * Recreates the singleton of a type and qualifier, and the beans that depend on it, as {@link #recreate(Object)}
     * does, when the context has created it. A singleton not created yet is left alone: it is created from the
     * current state when first requested.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, null for none
     * @param <T> The bean type
     * @return Whether a singleton of the type was held and recreated
     * @throws io.micronaut.context.exceptions.NonUniqueBeanException if the type and qualifier match several beans
     * @since 5.3.0
     */
    <T> boolean recreate(Argument<T> beanType, @Nullable Qualifier<T> qualifier);

    /**
     * Recreates the singleton of a type and qualifier, and the beans that depend on it, as
     * {@link #recreate(Argument, Qualifier)} does.
     *
     * @param beanType The bean type
     * @param qualifier The qualifier, null for none
     * @param <T> The bean type
     * @return Whether a singleton of the type was held and recreated
     * @since 5.3.0
     */
    default <T> boolean recreate(Class<T> beanType, @Nullable Qualifier<T> qualifier) {
        return recreate(Argument.of(beanType), qualifier);
    }
}
