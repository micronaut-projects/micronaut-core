/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty.channel.loom;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.Executor;

/**
 * Scheduler for a virtual thread, with metadata. Does not change throughout a virtual thread's
 * lifetime. This class allows for creating local shared resources, e.g. HTTP connections that
 * run on the same event loop.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
public sealed interface EventLoopVirtualThreadScheduler extends Executor
    permits LoomCarrierGroup.Runner, LoomCarrierGroup.StickyScheduler {

    /**
     * Get a shared {@link AttributeMap} for this scheduler.
     *
     * @return The attribute map
     */
    @NonNull
    AttributeMap attributeMap();

    /**
     * Get the event loop that runs on this scheduler.
     *
     * @return The event loop
     */
    @NonNull
    EventExecutor eventLoop();
}
