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
import io.micronaut.context.watch.ClassChangeWatchRequest;
import io.micronaut.context.watch.ConfigurationWatchRequest;
import io.micronaut.context.watch.DefinitionWatchRequest;
import io.micronaut.context.watch.MethodWatchRequest;
import io.micronaut.context.watch.ResourceWatchRequest;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;

/**
 * A bean context whose definitions, beans, methods, configuration, resources and classes can be watched: a
 * watcher describes the specific things it derives state from with a fluent request, in the style of the
 * {@code FileWatcher}, and receives one batch whenever they change, the state when it registered being the first
 * batch:
 *
 * <pre>
 * context.definitions(Codec.class).qualifier(qualifier).watch(change -&gt; rebuild(change.current()));
 * context.definitions(Rule.class).instances().onAdded(rules::add).onRemoved(rules::remove).watch();
 * context.methods(Scheduled.class).watch(this);
 * context.configuration("datasources.default").withFirstBatch().watch(change -&gt; resize());
 * context.resources(ResourceKind.VIEWS).include("**&#47;*.html").watch(change -&gt; evict(change));
 * context.classChanges().watch(change -&gt; cache.keySet().removeIf(change::isStaleType));
 * </pre>
 *
 * <h2>Batches and per-change handlers</h2>
 * <p>The batch is the unit of delivery and consistency. A development reload retires one generation's
 * definitions and adds the next one's in a single step, and a watch receives that step as one batch, applied
 * against a context whose state is already consistent: a watcher that aggregates what it selects, such as a
 * router built from every controller, never sees a half-applied reload, and a batch pairs a removed definition or
 * method with its new generation, so that a watcher can tell an edit from a removal. A watcher that does not
 * aggregate adds per-change handlers to its request instead, {@code onAdded}, {@code onRemoved} and
 * {@code onReplaced}, which are called for each change of a batch once it is applied.</p>
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
 * <p>Definition, instance, method and resource watches always have a first batch. A configuration watch has
 * one when its request asks for it with {@link ConfigurationWatchRequest#withFirstBatch()}, which calls the
 * watcher once with {@link io.micronaut.context.watch.ConfigurationChange#ofInitial()} to read the
 * configuration as it is. A class change watch has none: nothing has changed when it is registered.</p>
 * <p>Watches of the same kind are delivered to in {@link io.micronaut.core.order.Ordered} order of their
 * watchers, and a failing watcher or handler does not stop the others.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public sealed interface WatchableBeanContext extends BeanContext permits DefaultBeanContext {

    /**
     * Starts a request to watch the bean definitions of a type, or, continued with
     * {@link DefinitionWatchRequest#instances()} or {@link DefinitionWatchRequest#methods(Class)}, the beans or the
     * executable methods of those definitions. Nothing is registered until a terminal operation of the request is
     * called. The watch receives the definitions present as its first batch, at startup or at once when the context
     * is already running, then one batch per change: a definition registered at runtime, or the definitions a
     * development reload retires and adds.
     *
     * @param beanType The bean type
     * @param <T> The bean type
     * @return The request, which selects every definition of the type
     */
    <T> DefinitionWatchRequest<T> definitions(Argument<T> beanType);

    /**
     * Starts a request to watch the bean definitions of a type.
     *
     * @param beanType The bean type
     * @param <T> The bean type
     * @return The request, which selects every definition of the type
     * @see #definitions(Argument)
     */
    default <T> DefinitionWatchRequest<T> definitions(Class<T> beanType) {
        return definitions(Argument.of(beanType));
    }

    /**
     * Starts a request to watch every bean definition, narrowed with a qualifier or a stereotype, such as
     * {@code definitions().stereotype(Controller.class)}.
     *
     * @return The request, which selects every definition
     * @see #definitions(Argument)
     */
    default DefinitionWatchRequest<Object> definitions() {
        return definitions(Argument.OBJECT_ARGUMENT);
    }

    /**
     * Starts a request to watch the executable methods of every bean that carry an annotation, directly or as a
     * stereotype: the reload-aware form of an {@link io.micronaut.context.processor.ExecutableMethodProcessor}. The
     * watch receives the methods present as its first batch, then one batch per change. The methods of some beans
     * only are watched with {@link DefinitionWatchRequest#methods(Class)}.
     *
     * @param annotationType The annotation the methods carry
     * @param <A> The annotation type
     * @return The request
     */
    <A extends Annotation> MethodWatchRequest<A> methods(Class<A> annotationType);

    /**
     * Starts a request to watch every configuration change.
     *
     * @return The request
     * @see #configuration(String)
     */
    ConfigurationWatchRequest configuration();

    /**
     * Starts a request to watch the configuration under a prefix: the watcher is called, after the configuration
     * beans under the prefix were bound again, for every refresh that touches the prefix.
     *
     * @param prefix The prefix, such as {@code datasources.default}
     * @return The request
     */
    ConfigurationWatchRequest configuration(String prefix);

    /**
     * Starts a request to watch the resources of a kind of resource root, such as the views, narrowed by glob. The
     * watch receives what is under the roots as its first batch, then one batch per change.
     *
     * @param kind The kind of resource root
     * @return The request, which selects every file of the kind
     */
    ResourceWatchRequest resources(ResourceKind kind);

    /**
     * Starts a request to watch the class changes of a development reload: the watch for a cache keyed by class,
     * which has no first batch. In a context that is not in development mode, the watch is never registered.
     *
     * @return The request
     */
    ClassChangeWatchRequest classChanges();

    /**
     * Recreates a singleton the context holds, and the beans that depend on it: the transitive dependents the
     * {@link BeanDependencyGraph} records are destroyed first, the bean is destroyed and created again from its
     * definition, and the dependents are created again when they are next requested, on top of the new instance. This
     * is what a watcher does when a change it is told about cannot be applied to a bean in place, such as a cache of
     * serializers or validators built from classes that a development reload replaced.
     *
     * <p>Meant for development-time watches. The dependents are known only when the context
     * {@link BeanContextConfiguration#beanDependencyTrackingEnabled() tracks bean dependencies}, which by default only a
     * context in {@link io.micronaut.context.env.DevelopmentMode development mode} does; any other context recreates
     * nothing and returns false, rather than leave dependents holding the instance it destroyed.</p>
     *
     * <p>An {@link io.micronaut.context.processor.ExecutableMethodProcessor}, whether it is the bean recreated or one
     * of its dependents, is created again at once and given the methods the context gave it at startup, once, so
     * that it picks up again what it was processing; the destroyed instance is given nothing more.</p>
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
