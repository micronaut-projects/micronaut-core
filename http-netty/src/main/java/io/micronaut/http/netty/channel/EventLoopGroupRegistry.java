/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.netty.channel;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.netty.channel.EventLoopGroup;

import java.util.Optional;

/**
 * Registry of configured event loops.
 *
 * @author graemerocher
 * @since 2.0
 */
public interface EventLoopGroupRegistry {

    /**
     * Obtain a configured Event Loop Group from the registry.
     * @param name The name of the group
     * @return The event loop group if configured
     */
    Optional<EventLoopGroup> getEventLoopGroup(@NonNull String name);

    /**
     * Obtain a configured Event Loop Group from the registry.
     * @param name The name of the group
     * @return The event loop group if configured
     */
    Optional<EventLoopGroupConfiguration> getEventLoopGroupConfiguration(@NonNull String name);
}
