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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.LoomSupport;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorConfiguration;
import io.micronaut.scheduling.executor.ExecutorFactory;
import io.netty.util.concurrent.FastThreadLocal;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ThreadFactory;

/**
 * Factory that replaces the virtual {@link ThreadFactory} to pick the current event loop when
 * possible.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
@Experimental
@Factory
@Requires(condition = LoomSupport.LoomCondition.class)
@Requires(condition = PrivateLoomSupport.PrivateLoomCondition.class)
final class EventLoopLoomFactory {
    final FastThreadLocal<ThreadFactory> targetScheduler = new FastThreadLocal<>();

    @Named(TaskExecutors.VIRTUAL)
    @Singleton
    @Replaces(value = ThreadFactory.class, factory = ExecutorFactory.class, named = TaskExecutors.VIRTUAL)
    ThreadFactory eventLoopGroupThreadFactory(@Named(TaskExecutors.VIRTUAL) ExecutorConfiguration configuration) {
        if (!configuration.isVirtual()) {
            throw new IllegalStateException("Virtual executor should be virtual");
        }

        ThreadFactory delegate = LoomSupport.newVirtualThreadFactory("virtual-executor-");
        return r -> {
            ThreadFactory targetScheduler = EventLoopLoomFactory.this.targetScheduler.get();
            if (targetScheduler == null) {
                return delegate.newThread(r);
            } else {
                return targetScheduler.newThread(r);
            }
        };
    }
}
