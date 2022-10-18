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
package io.micronaut.scheduling;

/**
 * The names of common task schedulers.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface TaskExecutors {

    /**
     * The name of the {@link java.util.concurrent.ExecutorService} used to schedule I/O tasks. By
     * default, this is a {@link java.util.concurrent.Executors#newCachedThreadPool() cached thread pool}.
     */
    String IO = "io";

    /**
     * The name of the {@link java.util.concurrent.ExecutorService} used to schedule blocking tasks.
     * If available, this will use {@link #VIRTUAL virtual threads}. Otherwise it will fall back to
     * {@link #IO}.
     */
    String BLOCKING = "blocking";

    /**
     * Executor that runs tasks on virtual threads. This requires JDK 19+, and
     * {@code --enable-preview}.
     */
    String VIRTUAL = "virtual";

    /**
     * The name of the {@link java.util.concurrent.ScheduledExecutorService} used to schedule background tasks.
     */
    String SCHEDULED = "scheduled";

    /**
     * The name of the {@link java.util.concurrent.ScheduledExecutorService} used to run message consumers such as a Kafka or RabbitMQ listeners.
     */
    String MESSAGE_CONSUMER = "consumer";


}
