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
 * A request to watch the class changes of a development reload, started with
 * {@link io.micronaut.context.WatchableBeanContext#classChanges()}, and completed by
 * {@link #watch(ClassChangeWatcher)}: the watch for a cache keyed by class, which evicts what
 * {@link io.micronaut.context.reload.ClassChangeEvent#isStaleType(Class)} says belongs to a retired generation, or
 * for state derived from classes rather than from beans.
 *
 * <p>Classes change only in {@link io.micronaut.context.env.DevelopmentMode development mode}. In a context that is
 * not in development mode nothing is registered: the watch returned is already inactive, and the watcher is never
 * called nor kept.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface ClassChangeWatchRequest {

    /**
     * Registers the watcher, called with each {@link io.micronaut.context.reload.ClassChangeEvent} a launcher
     * publishes, before the listeners of the event. There is no first batch: nothing has changed when the watch is
     * registered.
     *
     * @param watcher The watcher
     * @return The watch, to close when the changes are no longer needed
     */
    BeanWatch watch(ClassChangeWatcher watcher);
}
