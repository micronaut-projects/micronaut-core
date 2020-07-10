/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.naming.Named;

import java.util.Optional;

/**
 * Default event loop group configuration interface.
 *
 * @author graemerocher
 * @since 2.0
 */
public interface EventLoopGroupConfiguration extends Named {
    /**
     * The configuration property prefix.
     */
    String EVENT_LOOPS = "micronaut.netty.event-loops";
    /**
     * The name of the default event loop group configuration.
     */
    String DEFAULT = "default";
    /**
     * The default.
     */
    String DEFAULT_LOOP = EVENT_LOOPS + "." + DEFAULT;

    /**
     * @return The number of threads for the event loop
     */
    int getNumThreads();

    /**
     * @return The I/O ratio.
     */
    Optional<Integer> getIoRatio();

    /**
     * @return The name of the executor to use.
     */
    Optional<String> getExecutorName();

    /**
     * @return Whether to prefer the native transport
     */
    boolean isPreferNativeTransport();
}
