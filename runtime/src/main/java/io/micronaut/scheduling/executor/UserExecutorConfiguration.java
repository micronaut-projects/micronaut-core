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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.validation.constraints.Min;
import java.util.Optional;
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

    protected String name;
    // needs to be protected to allow for nThreads to be set in config
    @SuppressWarnings("WeakerAccess")
    protected Integer nThreads;
    private ExecutorType type;
    private Integer parallelism;
    private Integer corePoolSize;
    private Class<? extends ThreadFactory> threadFactoryClass;

    /**
     * Private Constructor.
     *
     * @param name The name
     */
    private UserExecutorConfiguration(@Parameter String name) {
        this(name, null, null, null, null, null);
    }

    /**
     * Default Constructor.
     *
     * @param name the name
     * @param nThreads number of threads
     * @param type the type
     * @param parallelism the parallelism
     * @param corePoolSize the core pool size
     * @param threadFactoryClass the thread factory class
     */
    @Inject
    protected UserExecutorConfiguration(@Nullable @Parameter String name,
                                        @Nullable Integer nThreads,
                                        @Nullable ExecutorType type,
                                        @Nullable Integer parallelism,
                                        @Nullable Integer corePoolSize,
                                        @Nullable Class<? extends ThreadFactory> threadFactoryClass) {
        this.name = name;
        this.nThreads = nThreads == null ? AVAILABLE_PROCESSORS * 2 : nThreads;
        this.type = type == null ? ExecutorType.SCHEDULED : type;
        this.parallelism = parallelism == null ? AVAILABLE_PROCESSORS : parallelism;
        this.corePoolSize = corePoolSize == null ? AVAILABLE_PROCESSORS * 2 : corePoolSize;
        this.threadFactoryClass = threadFactoryClass;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public ExecutorType getType() {
        return type;
    }

    @Override
    @Min(1L)
    public Integer getParallelism() {
        return parallelism;
    }

    @Override
    @Min(1L)
    public Integer getNumberOfThreads() {
        return nThreads;
    }

    @Override
    @Min(1L)
    public Integer getCorePoolSize() {
        return corePoolSize;
    }

    @Override
    public Optional<Class<? extends ThreadFactory>> getThreadFactoryClass() {
        return Optional.ofNullable(threadFactoryClass);
    }

    /**
     * Sets the executor name.
     *
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the executor type. Default value ({@link io.micronaut.scheduling.executor.ExecutorType#SCHEDULED}).
     *
     * @param type The type
     */
    public void setType(ExecutorType type) {
        if (type != null) {
            this.type = type;
        }
    }

    /**
     * Sets the parallelism for {@link io.micronaut.scheduling.executor.ExecutorType#WORK_STEALING}. Default value (Number of processors available to the Java virtual machine).
     *
     * @param parallelism The parallelism
     */
    public void setParallelism(Integer parallelism) {
        if (parallelism != null) {
            this.parallelism = parallelism;
        }
    }

    /**
     * Sets the number of threads for {@link io.micronaut.scheduling.executor.ExecutorType#FIXED}. Default value (2 * Number of processors available to the Java virtual machine).
     *
     * @param nThreads The number of threads
     */

    public void setNumberOfThreads(Integer nThreads) {
        if (nThreads != null) {
            this.nThreads = nThreads;
        }
    }

    /**
     * Sets the core pool size for {@link io.micronaut.scheduling.executor.ExecutorType#SCHEDULED}. Default value (2 * Number of processors available to the Java virtual machine).
     *
     * @param corePoolSize The core pool size
     */
    public void setCorePoolSize(Integer corePoolSize) {
        if (corePoolSize != null) {
            this.corePoolSize = corePoolSize;
        }
    }

    /**
     * Sets the thread factory class.
     *
     * @param threadFactoryClass The thread factory class.
     */
    public void setThreadFactoryClass(Class<? extends ThreadFactory> threadFactoryClass) {
        this.threadFactoryClass = threadFactoryClass;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link io.micronaut.scheduling.executor.ExecutorType}.
     *
     * @param type The type
     * @return The configuration
     */
    public static UserExecutorConfiguration of(ExecutorType type) {
        ArgumentUtils.check("type", type).notNull();
        UserExecutorConfiguration configuration = new UserExecutorConfiguration(null);
        configuration.type = type;
        return configuration;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link io.micronaut.scheduling.executor.ExecutorType}.
     *
     * @param name The name
     * @param type The type
     * @return The configuration
     */
    public static UserExecutorConfiguration of(String name, ExecutorType type) {
        ArgumentUtils.check("name", name).notNull();
        ArgumentUtils.check("type", type).notNull();
        UserExecutorConfiguration configuration = new UserExecutorConfiguration(name);
        configuration.type = type;
        return configuration;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link io.micronaut.scheduling.executor.ExecutorType}.
     *
     * @param type The type
     * @param num  The number of threads for {@link io.micronaut.scheduling.executor.ExecutorType#FIXED} or the parallelism for
     *             {@link io.micronaut.scheduling.executor.ExecutorType#WORK_STEALING} or the core pool size for {@link io.micronaut.scheduling.executor.ExecutorType#SCHEDULED}
     * @return The configuration
     */
    public static UserExecutorConfiguration of(ExecutorType type, int num) {
        ArgumentUtils.check("type", type).notNull();
        UserExecutorConfiguration configuration = of(type);
        configuration.type = type;
        switch (type) {
            case FIXED:
                configuration.nThreads = num;
                break;
            case SCHEDULED:
                configuration.corePoolSize = num;
                break;
            case WORK_STEALING:
                configuration.parallelism = num;
                break;
            default:
        }
        return configuration;
    }

    /**
     * Construct a {@link UserExecutorConfiguration} for the given {@link io.micronaut.scheduling.executor.ExecutorType}.
     *
     * @param type               The type
     * @param num                The number of threads for {@link io.micronaut.scheduling.executor.ExecutorType#FIXED} or the parallelism for
     *                           {@link io.micronaut.scheduling.executor.ExecutorType#WORK_STEALING} or the core pool size for {@link io.micronaut.scheduling.executor.ExecutorType#SCHEDULED}
     * @param threadFactoryClass The thread factory class
     * @return The configuration
     */
    public static UserExecutorConfiguration of(ExecutorType type, int num, @Nullable Class<? extends ThreadFactory> threadFactoryClass) {
        UserExecutorConfiguration configuration = of(type, num);
        if (threadFactoryClass != null) {
            configuration.threadFactoryClass = threadFactoryClass;
        }
        return configuration;
    }
}
