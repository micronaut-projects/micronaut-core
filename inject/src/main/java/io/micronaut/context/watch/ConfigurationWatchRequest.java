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

/**
 * A request to watch the configuration, all of it or under a prefix, started with
 * {@link io.micronaut.context.WatchableBeanContext#configuration()} or
 * {@link io.micronaut.context.WatchableBeanContext#configuration(String)}, and completed by
 * {@link #watch(ConfigurationWatcher)} or {@link #watchReloading(ReloadingConfigurationWatcher)}. The watcher is
 * called, after the configuration beans under the prefix were bound again, for every refresh that touches the
 * prefix.
 *
 * <pre>
 * context.configuration("datasources.default")
 *     .withFirstBatch()
 *     .watch(change -&gt; pool.resize(configuration.getMaximumPoolSize()));
 * </pre>
 *
 * <p>A watch registered while a bean is being created belongs to that bean, which is what lets a reloading watcher
 * answer {@link ReloadingConfigurationWatcher.Outcome#RECREATE}. Each terminal operation takes a snapshot of the
 * request, so a request may be reused for further watches.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface ConfigurationWatchRequest {

    /**
     * Starts the watch with a first batch, as the other watches always do: the watcher is called once as it is
     * registered, or as the context starts when it has not started yet, with a change whose
     * {@link ConfigurationChange#initial()} holds, to read the configuration as it is then. Every refresh after that
     * read reaches the watcher, which a read of its own before registering cannot promise. Without it, the watcher is
     * called for the refreshes only.
     *
     * @return This request
     */
    ConfigurationWatchRequest withFirstBatch();

    /**
     * Registers a watcher that applies every change it is given.
     *
     * @param watcher The watcher
     * @return The watch, to close when the changes are no longer needed
     */
    BeanWatch watch(ConfigurationWatcher watcher);

    /**
     * Registers a watcher that says what it did with each change, such as a bean that must be recreated, or a
     * server that must be restarted for a change to apply. It is named apart from {@link #watch(ConfigurationWatcher)}
     * so that a lambda is not ambiguous between the two.
     *
     * @param watcher The watcher
     * @return The watch, to close when the changes are no longer needed
     */
    BeanWatch watchReloading(ReloadingConfigurationWatcher watcher);
}
