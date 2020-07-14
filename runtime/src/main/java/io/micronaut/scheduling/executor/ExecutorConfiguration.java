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
package io.micronaut.scheduling.executor;

import edu.umd.cs.findbugs.annotations.Nullable;

import javax.validation.constraints.Min;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ExecutorConfiguration {

    /**
     * The prefix used for configuration.
     */
    String PREFIX = "micronaut.executors";

    /**
     * The prefix used for I/O pool configuration.
     */
    String PREFIX_IO = PREFIX + ".io";

    /**
     * The prefix used for Scheduled task configuration.
     */
    String PREFIX_SCHEDULED = PREFIX + ".scheduled";

    /**
     * The prefix used for Scheduled task configuration.
     */
    String PREFIX_CONSUMER = PREFIX + ".consumer";

    /**
     * @return The name of the component
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * @return The {@link io.micronaut.scheduling.executor.ExecutorType}
     */
    ExecutorType getType();

    /**
     * @return The parallelism for {@link io.micronaut.scheduling.executor.ExecutorType#WORK_STEALING}
     */
    @Min(1L) Integer getParallelism();

    /**
     * @return The number of threads for {@link io.micronaut.scheduling.executor.ExecutorType#FIXED}
     */
    @Min(1L) Integer getNumberOfThreads();

    /**
     * @return The core pool size for {@link io.micronaut.scheduling.executor.ExecutorType#SCHEDULED}
     */
    @Min(1L) Integer getCorePoolSize();

    /**
     * @return The class to use as the {@link ThreadFactory}
     */
    Optional<Class<? extends ThreadFactory>> getThreadFactoryClass();
}
