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

/**
 * An enum modelling different {@link java.util.concurrent.Executor} types that mirror the methods defined in the
 * {@link java.util.concurrent.Executors} class.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum ExecutorType {

    /**
     * @see java.util.concurrent.Executors#newScheduledThreadPool(int)
     */
    SCHEDULED,

    /**
     * @see java.util.concurrent.Executors#newCachedThreadPool()
     */
    CACHED,

    /**
     * @see java.util.concurrent.Executors#newFixedThreadPool(int)
     */
    FIXED,

    /**
     * @see java.util.concurrent.Executors#newWorkStealingPool()
     */
    WORK_STEALING
}
