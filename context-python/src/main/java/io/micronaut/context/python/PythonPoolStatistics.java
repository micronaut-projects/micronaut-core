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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Introspected;

/**
 * A snapshot of the Python context pool.
 *
 * @param enabled Whether pooling is enabled
 * @param targetSize The number of pooled contexts the pool grows to
 * @param pooledContexts The pooled contexts created so far
 * @param idleContexts The pooled contexts waiting to be borrowed
 * @param eventLoopContexts The contexts dedicated to Netty event loops
 * @param borrows How many times a context was requested from the pool
 * @param waits How many of those requests had to wait for a context
 * @param totalWaitMillis The time all waiting requests spent waiting
 * @param maxWaitMillis The longest single wait
 * @param closed Whether the pool has been closed
 * @since 5.2.0
 */
@Experimental
@Introspected
public record PythonPoolStatistics(
    boolean enabled,
    int targetSize,
    int pooledContexts,
    int idleContexts,
    int eventLoopContexts,
    long borrows,
    long waits,
    long totalWaitMillis,
    long maxWaitMillis,
    boolean closed
) {
}
