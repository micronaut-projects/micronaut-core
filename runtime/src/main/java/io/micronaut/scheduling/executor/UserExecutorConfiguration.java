/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.scheduling.executor;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadFactory;

/**
 * Allows configuration {@link java.util.concurrent.ExecutorService} instances that are made available as beans.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachProperty(value = ExecutorConfiguration.PREFIX)
public class UserExecutorConfiguration implements ExecutorConfiguration {

    /**
     * Number of available processors.
     */
    public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    protected Optional<ExecutorType> type = Optional.of(ExecutorType.SCHEDULED);
    protected OptionalInt parallelism = OptionalInt.of(AVAILABLE_PROCESSORS);
    protected OptionalInt nThreads = OptionalInt.of(AVAILABLE_PROCESSORS * 2);
    protected OptionalInt corePoolSize = OptionalInt.of(AVAILABLE_PROCESSORS * 2);
    protected Optional<Class<? extends ThreadFactory>> threadFactoryClass = Optional.empty();

    /**
     * Default Constructor.
     */
    protected UserExecutorConfiguration() {
    }

    @Override
    public ExecutorType getType() {
        return type.orElse(ExecutorType.SCHEDULED);
    }

    @Override
    @Min(1L)
    public Integer getParallelism() {
        return parallelism.orElse(AVAILABLE_PROCESSORS);
    }

    @Override
    @Min(1L)
    public Integer getNumberOfThreads() {
        return nThreads.orElse(AVAILABLE_PROCESSORS);
    }

    @Override
    @Min(1L)
    public Integer getCorePoolSize() {
        return corePoolSize.orElse(AVAILABLE_PROCESSORS);
    }

    @Override
    public Optional<Class<? extends ThreadFactory>> getThreadFactoryClass() {
        return threadFactoryClass;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link ExecutorType}.
     *
     * @param type The type
     * @return The configuration
     */
    public static UserExecutorConfiguration of(ExecutorType type) {
        ArgumentUtils.check("type", type).notNull();
        UserExecutorConfiguration configuration = new UserExecutorConfiguration();
        configuration.type = Optional.of(type);
        return configuration;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link ExecutorType}.
     *
     * @param type The type
     * @param num  The number of threads for {@link ExecutorType#FIXED} or the parallelism for
     *             {@link ExecutorType#WORK_STEALING} or the core pool size for {@link ExecutorType#SCHEDULED}
     * @return The configuration
     */
    public static UserExecutorConfiguration of(ExecutorType type, int num) {
        UserExecutorConfiguration configuration = of(type);
        configuration.type = Optional.of(type);
        switch (type) {
            case FIXED:
                configuration.nThreads = OptionalInt.of(num);
                break;
            case SCHEDULED:
                configuration.corePoolSize = OptionalInt.of(num);
                break;
            case WORK_STEALING:
                configuration.parallelism = OptionalInt.of(num);
                break;
            default:
        }
        return configuration;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link ExecutorType}.
     *
     * @param type               The type
     * @param num                The number of threads for {@link ExecutorType#FIXED} or the parallelism for
     *                           {@link ExecutorType#WORK_STEALING} or the core pool size for {@link ExecutorType#SCHEDULED}
     * @param threadFactoryClass The thread factory class
     * @return The configuration
     */
    public static UserExecutorConfiguration of(ExecutorType type, int num, @Nullable Class<? extends ThreadFactory> threadFactoryClass) {
        UserExecutorConfiguration configuration = of(type, num);
        if (threadFactoryClass != null) {
            configuration.threadFactoryClass = Optional.of(threadFactoryClass);
        }
        return configuration;
    }
}
