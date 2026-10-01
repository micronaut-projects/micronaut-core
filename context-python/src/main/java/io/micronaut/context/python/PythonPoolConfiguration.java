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
package io.micronaut.context.python;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.bind.annotation.Bindable;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for the PythonPool.
 *
 * @param enabled    Whether pooling is enabled.
 * @param size       The size of the pool. Defaults to the number of processors divided by four, at least 2 and at most 8. More contexts cost throughput on a route that reaches a Python bean that is not itself pooled, so raise this only for a workload measured to want it.
 * @param warnWait   The amount of time to wait for a pooled context before a warning is printed. Defaults to two seconds; zero or a negative duration turns the warning off.
 * @param maxEventLoopContexts The most Netty event loops that get a dedicated asyncio context; {@code 0}, the default, gives every event loop one. Event loops beyond the cap run Python through the shared pool and block while a coroutine runs.
 * @param ignoreDependencies The singleton Python dependencies of pooled types not to warn about, by simple or qualified type name; {@code *} warns about none. Read when the Python sources are processed, not at run time: Pyronaut passes it to the compiler as the {@code micronaut.python.pool.ignoreDependencies} annotation processor option.
 */
@ConfigurationProperties(PythonPoolConfiguration.PREFIX)
@Experimental
public record PythonPoolConfiguration(
    @Bindable(defaultValue = "true") boolean enabled,
    @Bindable(defaultValue = "0") int size,
    @Bindable(defaultValue = "2s") @Nullable Duration warnWait,
    @Bindable(defaultValue = "0") int maxEventLoopContexts,
    @Nullable List<String> ignoreDependencies
) {
    /** The configuration prefix. */
    public static final String PREFIX = "micronaut.python.pool";

    /**
     * The configuration without dependencies left unreported, which only Python processing reads.
     *
     * @param enabled Whether pooling is enabled
     * @param size The size of the pool
     * @param warnWait The amount of time to wait for a pooled context before a warning is printed
     * @param maxEventLoopContexts The most Netty event loops that get a dedicated asyncio context
     */
    public PythonPoolConfiguration(boolean enabled, int size, @Nullable Duration warnWait, int maxEventLoopContexts) {
        this(enabled, size, warnWait, maxEventLoopContexts, null);
    }
}
