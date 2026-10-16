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
 * A request to watch the resources of a kind of resource root, started with
 * {@link io.micronaut.context.WatchableBeanContext#resources(io.micronaut.context.reload.ResourceKind)}, and
 * completed by {@link #watch(ResourceWatcher)}. The watch receives what is under the roots as its first batch, then
 * one batch per change.
 *
 * <pre>
 * context.resources(ResourceKind.VIEWS)
 *     .include("**&#47;*.html")
 *     .watch(change -&gt; templates.evict(change.changed()));
 * </pre>
 *
 * <p>Each terminal operation takes a snapshot of the request, so a request may be reused, and changed, for further
 * watches.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface ResourceWatchRequest {

    /**
     * Adds globs a file must match, relative to a root of the kind, to be delivered. Without any, every file is. A
     * glob starting with <code>**&#47;</code> also matches a file directly under a root.
     *
     * @param globs The globs, such as <code>**&#47;*.html</code>
     * @return This request
     */
    ResourceWatchRequest include(String... globs);

    /**
     * Registers the watcher.
     *
     * @param watcher The watcher, given the changes narrowed to the files selected
     * @return The watch, to close when the changes are no longer needed
     */
    BeanWatch watch(ResourceWatcher watcher);
}
