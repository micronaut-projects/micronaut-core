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
package io.micronaut.context.watch;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

/**
 * A request to watch the bean definitions of a type, started with
 * {@link io.micronaut.context.WatchableBeanContext#definitions(io.micronaut.core.type.Argument)}: it selects the
 * definitions, and is completed by one of its terminal operations, which choose what is watched of them.
 *
 * <ul>
 * <li>The definitions themselves: {@link #watch(BeanDefinitionWatcher)} for whole batches, or {@link #onAdded},
 * {@link #onRemoved} and {@link #onReplaced} for one change at a time, completed by {@link #watch()}.</li>
 * <li>The beans of the definitions: {@link #instances()}.</li>
 * <li>The executable methods of the definitions that carry an annotation: {@link #methods(Class)}.</li>
 * </ul>
 *
 * <pre>
 * BeanWatch watch = context.definitions(Codec.class)
 *     .qualifier(Qualifiers.byName("json"))
 *     .onAdded(definition -&gt; register(definition))
 *     .onRemoved(definition -&gt; unregister(definition))
 *     .watch();
 * </pre>
 *
 * <p>A batch is the unit of delivery: a watch receives the definitions selected as its first batch, then one batch
 * per change, such as the definitions a development reload retires and adds at once, so a watcher that aggregates
 * what it selects never sees a half-applied reload. The per-change handlers are called for each definition of a
 * batch once the batch is applied, after the batch watcher if there is one.</p>
 *
 * <p>An {@link io.micronaut.context.annotation.EachProperty @EachProperty} or
 * {@link io.micronaut.context.annotation.EachBean @EachBean} definition is selected as the context resolves it:
 * one definition per entry of its configuration, or per bean it is created for, in every batch.</p>
 *
 * <p>Each terminal operation takes a snapshot of the request, so a request may be reused, and changed, for further
 * watches.</p>
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface DefinitionWatchRequest<T> {

    /**
     * Selects only the definitions a qualifier accepts. Called again, the qualifiers must all accept a definition.
     *
     * @param qualifier The qualifier
     * @return This request
     */
    DefinitionWatchRequest<T> qualifier(Qualifier<T> qualifier);

    /**
     * Selects only the definitions of beans carrying an annotation, directly or as a stereotype, such as
     * {@code Controller}: a shorthand for {@code qualifier(Qualifiers.byStereotype(stereotype))}.
     *
     * @param stereotype The annotation
     * @return This request
     */
    DefinitionWatchRequest<T> stereotype(Class<? extends Annotation> stereotype);

    /**
     * Selects only the definitions of beans carrying an annotation, directly or as a stereotype, named by its class
     * name: a shorthand for {@code qualifier(Qualifiers.byStereotype(annotationName))}. A module matches an annotation
     * of an optional dependency this way without referencing its class, which may not be on the classpath; when it is
     * not, no definition carries the annotation and the request selects none.
     *
     * @param annotationName The fully qualified name of the annotation, such as {@code io.micronaut.http.annotation.Controller}
     * @return This request
     */
    DefinitionWatchRequest<T> stereotype(String annotationName);

    /**
     * Adds a handler called with each definition added, once the batch that adds it is applied. For the first
     * batch, it is called with every definition selected.
     *
     * @param handler The handler
     * @return This request
     */
    DefinitionWatchRequest<T> onAdded(Consumer<? super BeanDefinition<T>> handler);

    /**
     * Adds a handler called with each definition removed, once the batch that removes it is applied. A definition
     * replaced by a new generation of itself is given to {@link #onReplaced} instead, when there is such a handler.
     *
     * @param handler The handler
     * @return This request
     */
    DefinitionWatchRequest<T> onRemoved(Consumer<? super BeanDefinition<T>> handler);

    /**
     * Adds a handler called with each definition removed and added again as a new generation of the same bean, as a
     * development reload does when the bean's class changed. Without this handler, such a definition is given to
     * {@link #onRemoved} and its replacement to {@link #onAdded}.
     *
     * @param handler The handler
     * @return This request
     */
    DefinitionWatchRequest<T> onReplaced(Consumer<? super BeanDefinitionChange.Replacement<T>> handler);

    /**
     * Registers a watch that calls the per-change handlers added to this request.
     *
     * @return The watch, to close when the changes are no longer needed
     * @throws IllegalStateException if no handler was added
     */
    BeanWatch watch();

    /**
     * Registers a watch that calls a watcher with each batch, then the per-change handlers added to this request.
     *
     * @param watcher The watcher
     * @return The watch, to close when the changes are no longer needed
     */
    BeanWatch watch(BeanDefinitionWatcher<T> watcher);

    /**
     * Continues the request as a watch of the beans of the definitions selected, which it creates. The handlers
     * added to this request are not carried over.
     *
     * @return The request for the instances
     */
    InstanceWatchRequest<T> instances();

    /**
     * Continues the request as a watch of the executable methods carrying an annotation, directly or as a
     * stereotype, of the definitions selected. The handlers added to this request are not carried over.
     *
     * @param annotationType The annotation
     * @param <A> The annotation type
     * @return The request for the methods
     */
    <A extends Annotation> MethodWatchRequest<A> methods(Class<A> annotationType);
}
