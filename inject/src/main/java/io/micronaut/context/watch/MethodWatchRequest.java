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

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;

/**
 * A request to watch the executable methods carrying an annotation, directly or as a stereotype: those of every
 * bean, started with {@link io.micronaut.context.WatchableBeanContext#methods(Class)}, or those of the definitions a
 * {@link DefinitionWatchRequest} selected, continued with {@link DefinitionWatchRequest#methods(Class)}. It is the
 * reload-aware form of an {@link io.micronaut.context.processor.ExecutableMethodProcessor}: the watch receives the
 * methods present as its first batch, then one batch per change, which pairs a method with its new generation.
 *
 * <pre>
 * context.methods(Scheduled.class)
 *     .onAdded(method -&gt; schedule(method))
 *     .onRemoved(method -&gt; cancel(method))
 *     .onReplaced(replacement -&gt; reschedule(replacement))
 *     .watch();
 * </pre>
 *
 * <p>Each terminal operation takes a snapshot of the request, so a request may be reused for further watches.</p>
 *
 * @param <A> The annotation type
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface MethodWatchRequest<A extends Annotation> {

    /**
     * Adds a handler called with each method added, once the batch that adds it is applied. For the first batch, it
     * is called with every method selected.
     *
     * @param handler The handler
     * @return This request
     */
    MethodWatchRequest<A> onAdded(Consumer<? super BeanExecutableMethod<A>> handler);

    /**
     * Adds a handler called with each method removed, once the batch that removes it is applied. A method replaced
     * by a new generation of itself is given to {@link #onReplaced} instead, when there is such a handler.
     *
     * @param handler The handler
     * @return This request
     */
    MethodWatchRequest<A> onRemoved(Consumer<? super BeanExecutableMethod<A>> handler);

    /**
     * Adds a handler called with each method removed and added again as a new generation of the same method: the
     * same bean, name and parameter types. Without this handler, the method is given to {@link #onRemoved} and its
     * replacement to {@link #onAdded}.
     *
     * @param handler The handler
     * @return This request
     */
    MethodWatchRequest<A> onReplaced(Consumer<? super ExecutableMethodChange.Replacement<A>> handler);

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
    BeanWatch watch(ExecutableMethodWatcher<A> watcher);
}
