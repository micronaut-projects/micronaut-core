/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.annotation.Scheduled;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The invocation of a {@link Scheduled} method by the scheduler, as seen from inside that invocation.
 *
 * <p>An interceptor of a scheduled method cannot otherwise tell a call the scheduler made from a call the
 * application made directly, nor, for a method with several schedules, which of them triggered the call. While the
 * scheduler invokes the method, {@link #current()} answers the method and the schedule that triggered it, on the
 * thread the method is invoked on.</p>
 *
 * @param method   The scheduled method
 * @param schedule The {@link Scheduled} annotation that triggered the invocation
 * @author Denis Stepanov
 * @since 5.3.0
 */
public record ScheduledExecution(ExecutableMethod<?, ?> method, AnnotationValue<Scheduled> schedule) {

    private static final ThreadLocal<ScheduledExecution> CURRENT = new ThreadLocal<>();

    /**
     * The scheduled invocation running on the current thread.
     *
     * <p>It covers the whole of the invocation, so a method the scheduled method calls sees it as well; compare
     * {@link #method()} with the method being invoked to tell the two apart.</p>
     *
     * @return The scheduled invocation, or empty when the scheduler is not invoking a method on this thread
     */
    public static Optional<ScheduledExecution> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Marks the start of a scheduled invocation on the current thread.
     *
     * @param execution The invocation
     * @return The invocation it nests in, to hand to {@link #exit(ScheduledExecution)}
     */
    @Internal
    public static @Nullable ScheduledExecution enter(ScheduledExecution execution) {
        ScheduledExecution previous = CURRENT.get();
        CURRENT.set(execution);
        return previous;
    }

    /**
     * Marks the end of a scheduled invocation on the current thread.
     *
     * @param previous What {@link #enter(ScheduledExecution)} returned
     */
    @Internal
    public static void exit(@Nullable ScheduledExecution previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
