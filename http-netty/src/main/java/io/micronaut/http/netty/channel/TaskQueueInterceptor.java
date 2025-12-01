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
package io.micronaut.http.netty.channel;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NonNull;

import java.util.Queue;

/**
 * An interceptor that wraps a task queue for a netty event loop.
 *
 * @author Jonas Konrad
 */
@BootstrapContextCompatible
@Experimental
public interface TaskQueueInterceptor {
    /**
     * Wrap a task queue.
     *
     * @param groupName The event loop group name
     * @param original  The original queue
     * @return The wrapped queue
     */
    @NonNull
    Queue<Runnable> wrapTaskQueue(@NonNull String groupName, @NonNull Queue<Runnable> original);
}
