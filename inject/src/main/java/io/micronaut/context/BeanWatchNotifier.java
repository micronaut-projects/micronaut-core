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

import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ReloadingConfigurationWatcher;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;

import java.util.Collection;
import java.util.List;

/**
 * The side of a {@link WatchableBeanContext} that delivers changes to its watches: called by the framework code that
 * makes the changes, such as a development launcher, the configuration refresh, the file watcher and the event
 * publisher, never by a module that watches. Implemented by the default context only; a caller tests for it with
 * {@code instanceof} and delivers nothing when the context is another one.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public sealed interface BeanWatchNotifier permits DefaultBeanContext {

    /**
     * Delivers a change of definitions to the watches: the removed definitions are no longer resolvable,
     * the added ones are. A development launcher calls this once per reload, after swapping the definitions.
     *
     * @param removed The definitions retired
     * @param added The definitions added
     */
    void notifyDefinitionChange(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added);

    /**
     * Delivers a configuration change to the watches whose prefix it touches. The configuration refresh
     * calls this after rebinding the configuration beans; a watch answering
     * {@link ReloadingConfigurationWatcher.Outcome#RECREATE} has its bean recreated before this returns.
     *
     * @param change The change
     * @return The outcomes of the watches delivered to
     */
    List<ReloadingConfigurationWatcher.Outcome> notifyConfigurationChange(ConfigurationChange change);

    /**
     * Delivers a resource change to the watches its kind and files concern. An initial change is kept
     * as the state of its kind, which a watch registered later receives as its first batch.
     *
     * @param change The change
     */
    void notifyResourceChange(ResourceChange change);

    /**
     * Delivers a class change to the class change watches, on the calling thread. The event publisher calls this for
     * every {@link ClassChangeEvent} it publishes, ahead of the listeners of the event and on the thread that notifies
     * them.
     *
     * @param change The change
     */
    void notifyClassChange(ClassChangeEvent change);

    /**
     * @return Whether a class change watch is registered and active, so that a publisher of class changes is not empty
     */
    boolean hasClassChangeWatches();

    /**
     * The processors fed through an adapter, which see additions only and cannot follow a reload. A processor is
     * adapted only in a context that tracks bean dependencies or runs in development mode.
     *
     * @return The adapted processors
     */
    Collection<Object> adaptedProcessors();
}
