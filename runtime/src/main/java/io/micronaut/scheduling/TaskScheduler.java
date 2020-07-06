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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;

/**
 * Interface for Scheduling tasks.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface TaskScheduler {

    /**
     * Creates and executes a one-shot action that becomes enabled
     * after the given delay.
     *
     * @param cron    The cron expression
     * @param command the task to execute
     * @return a ScheduledFuture representing pending completion of
     * the task and whose {@code get()} method will return
     * {@code null} upon completion
     * @throws java.util.concurrent.RejectedExecutionException if the task cannot be
     *                                                         scheduled for execution
     * @throws NullPointerException                            if command or delay is null
     */
    ScheduledFuture<?> schedule(String cron, Runnable command);

    /**
     * Creates and executes a one-shot action that becomes enabled
     * after the given delay.
     *
     * @param cron    The cron expression
     * @param command The task to execute
     * @param <V>     The type of the callable's result
     * @return a ScheduledFuture representing pending completion of
     * the task and whose {@code get()} method will return
     * {@code null} upon completion
     * @throws java.util.concurrent.RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command or delay is null
     */
    <V> ScheduledFuture<V> schedule(String cron, Callable<V> command);

    /**
     * Creates and executes a one-shot action that becomes enabled
     * after the given delay.
     *
     * @param delay   the time from now to delay execution
     * @param command the task to execute
     * @return a ScheduledFuture representing pending completion of
     * the task and whose {@code get()} method will return
     * {@code null} upon completion
     * @throws java.util.concurrent.RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command or delay is null
     */
    ScheduledFuture<?> schedule(Duration delay, Runnable command);

    /**
     * Creates and executes a ScheduledFuture that becomes enabled after the
     * given delay.
     *
     * @param delay    The time from now to delay execution
     * @param callable The function to execute
     * @param <V>      The type of the callable's result
     * @return a ScheduledFuture that can be used to extract result or cancel
     * @throws java.util.concurrent.RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if callable or delay is null
     */
    <V> ScheduledFuture<V> schedule(Duration delay, Callable<V> callable);

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the given
     * period; that is executions will commence after
     * {@code initialDelay} then {@code initialDelay+period}, then
     * {@code initialDelay + 2 * period}, and so on.
     * If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.  If any execution of this task
     * takes longer than its period, then subsequent executions
     * may start late, but will not concurrently execute.
     *
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param command      the task to execute
     * @return a ScheduledFuture representing pending completion of
     * the task, and whose {@code get()} method will throw an
     * exception upon cancellation
     * @throws java.util.concurrent.RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command or period is null
     * @throws IllegalArgumentException   if period less than or equal to zero
     */
    ScheduledFuture<?> scheduleAtFixedRate(@Nullable Duration initialDelay,
                                           Duration period,
                                           Runnable command);

    /**
     * Creates and executes a periodic action that becomes enabled first
     * after the given initial delay, and subsequently with the
     * given delay between the termination of one execution and the
     * commencement of the next.  If any execution of the task
     * encounters an exception, subsequent executions are suppressed.
     * Otherwise, the task will only terminate via cancellation or
     * termination of the executor.
     *
     * @param initialDelay the time to delay first execution
     * @param delay        the delay between the termination of one
     * @param command      the task to execute
     *                     execution and the commencement of the next
     * @return a ScheduledFuture representing pending completion of
     * the task, and whose {@code get()} method will throw an
     * exception upon cancellation
     * @throws java.util.concurrent.RejectedExecutionException if the task cannot be
     *                                    scheduled for execution
     * @throws NullPointerException       if command or delay is null
     * @throws IllegalArgumentException   if delay less than or equal to zero
     */
    ScheduledFuture<?> scheduleWithFixedDelay(@Nullable Duration initialDelay,
                                              Duration delay,
                                              Runnable command);
}
