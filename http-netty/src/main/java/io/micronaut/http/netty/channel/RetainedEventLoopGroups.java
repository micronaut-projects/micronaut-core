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
package io.micronaut.http.netty.channel;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.EventLoopGroup;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Event loop groups that outlive the {@link DefaultEventLoopGroupRegistry} that asked for them. The registry asks
 * for a group by name instead of creating it, when the group runs on the framework's own threads: no executor bean,
 * no thread factory bean, no loom carriers and no task queue interceptors, none of which may outlive the context
 * that created them. When the registry shuts down, the groups it was handed are left running.
 *
 * <p>Development mode provides it, so that the event loops of the HTTP servers and clients, their threads and
 * their selectors, are kept while one generation of the application stops and the next one starts. What runs on
 * them, the channels, their pipelines and their handlers, is each generation's own.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public interface RetainedEventLoopGroups {

    /**
     * The group retained under a name, when it was made from the same settings by the same kind of factory, or the
     * one {@code create} makes, retained from then on unless the configuration or the factory is not one that may
     * outlive the context.
     *
     * @param name The name of the group
     * @param configuration The configuration it is made from, whose settings it is retained for, and whose shutdown
     * periods apply when it is released
     * @param factory The factory that makes its I/O handlers and channels
     * @param create Creates the group when none is retained under the name for these settings
     * @return The group
     */
    EventLoopGroup eventLoopGroup(String name, EventLoopGroupConfiguration configuration, EventLoopGroupFactory factory, Supplier<EventLoopGroup> create);

    /**
     * Called as the registry shuts down, with the groups it was handed and made: those that are retained are left
     * running for the next registry.
     *
     * @param groups The groups of the registry
     * @return The groups the registry leaves running
     */
    Set<EventLoopGroup> release(Collection<EventLoopGroup> groups);
}
