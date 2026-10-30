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

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Experimental;

import java.util.function.Consumer;

/**
 * A request to watch the beans of the definitions a {@link DefinitionWatchRequest} selected, continued with
 * {@link DefinitionWatchRequest#instances()}, and completed by {@link #watch(BeanInstanceWatcher)} or
 * {@link #watch()}. The watch creates the beans: it receives them as its first batch, then one batch per change,
 * with the instances that went and the ones that came. The instances delivered stay the ones delivered: a
 * prototype among the candidates is created once for the watch, not again for every batch.
 *
 * <pre>
 * context.definitions(Rule.class).instances()
 *     .onAdded(registration -&gt; rules.add(registration.bean()))
 *     .onRemoved(registration -&gt; rules.remove(registration.bean()))
 *     .watch();
 * </pre>
 *
 * <p>Who destroys an instance delivered:</p>
 * <ul>
 * <li>A bean no scope holds, such as a {@link io.micronaut.context.annotation.Prototype prototype}, is
 * created for the watch, which owns it: the watch destroys it, its {@code @PreDestroy} methods and the
 * dependent beans it owns included, once the batch that removes it was delivered, or when the watch is
 * closed, by {@link BeanWatch#close()}, with the bean that registered the watch, or when the context
 * stops. The watcher does not destroy it, and does not use a removed instance after the batch that
 * removed it, nor any instance after the watch was closed.</li>
 * <li>A singleton, or a bean of a custom scope, belongs to its scope, which destroys it as it would
 * without the watch: a removed one may already be destroyed, or still be in use elsewhere.</li>
 * </ul>
 *
 * <p>Each terminal operation takes a snapshot of the request, so a request may be reused for further watches.</p>
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface InstanceWatchRequest<T> {

    /**
     * Adds a handler called with each bean added, once the batch that adds it is applied. For the first batch, it is
     * called with every bean selected.
     *
     * @param handler The handler
     * @return This request
     */
    InstanceWatchRequest<T> onAdded(Consumer<? super BeanRegistration<T>> handler);

    /**
     * Adds a handler called with each bean removed, once the batch that removes it is applied, and before the watch
     * destroys an instance it owns. A bean replaced by one of a new generation of its definition is given to
     * {@link #onReplaced} instead, when there is such a handler.
     *
     * @param handler The handler
     * @return This request
     */
    InstanceWatchRequest<T> onRemoved(Consumer<? super BeanRegistration<T>> handler);

    /**
     * Adds a handler called with each bean removed and replaced by the bean of a new generation of its definition.
     * Without this handler, the bean is given to {@link #onRemoved} and its replacement to {@link #onAdded}.
     *
     * @param handler The handler
     * @return This request
     */
    InstanceWatchRequest<T> onReplaced(Consumer<? super BeanInstanceChange.Replacement<T>> handler);

    /**
     * Registers a watch that calls the per-change handlers added to this request.
     *
     * @return The watch, to close when the changes are no longer needed; closing it destroys the beans it owns
     * @throws IllegalStateException if no handler was added
     */
    BeanWatch watch();

    /**
     * Registers a watch that calls a watcher with each batch, then the per-change handlers added to this request.
     *
     * @param watcher The watcher
     * @return The watch, to close when the changes are no longer needed; closing it destroys the beans it owns
     */
    BeanWatch watch(BeanInstanceWatcher<T> watcher);
}
