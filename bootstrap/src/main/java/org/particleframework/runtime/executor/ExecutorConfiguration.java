/*
 * Copyright 2017 original authors
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
package org.particleframework.runtime.executor;

import org.particleframework.context.annotation.ForEach;

import javax.validation.constraints.Min;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;

/**
 * Allows configuration {@link java.util.concurrent.ExecutorService} instances that are made available as beans
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ForEach(property = "particle.server.executors")
public class ExecutorConfiguration {

    protected Optional<ExecutorType> type = Optional.of(ExecutorType.SCHEDULED);
    protected Optional<Integer> parallelism = Optional.of(Runtime.getRuntime().availableProcessors());
    protected Optional<Integer> nThreads = Optional.of(Runtime.getRuntime().availableProcessors());
    protected Optional<Integer> corePoolSize = Optional.of(Runtime.getRuntime().availableProcessors());
    protected Optional<Class<? extends ThreadFactory>> threadFactoryClass = Optional.empty();

    /**
     * @return The {@link ExecutorType}
     */
    public ExecutorType getType() {
        return type.orElse(ExecutorType.SCHEDULED);
    }

    /**
     * @return The parallelism for {@link ExecutorType#WORK_STEALING}
     */
    @Min(1L)
    public Integer getParallelism() {
        return parallelism.orElse(Runtime.getRuntime().availableProcessors());
    }

    /**
     * @return The number of threads for {@link ExecutorType#FIXED}
     */
    @Min(1L)
    public Integer getNumberOfThreads() {
        return nThreads.orElse(Runtime.getRuntime().availableProcessors());
    }

    /**
     * @return The number of threads for {@link ExecutorType#FIXED}
     */
    @Min(1L)
    public Integer getCorePoolSize() {
        return corePoolSize.orElse(Runtime.getRuntime().availableProcessors());
    }

    /**
     * @return The class to use as the {@link ThreadFactory}
     */
    public Optional<Class<? extends ThreadFactory>> getThreadFactoryClass() {
        return threadFactoryClass;
    }
}
