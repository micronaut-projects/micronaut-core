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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.annotation.Scheduled;

import java.util.Optional;

/**
 * The invocation of a {@link Scheduled} method by the scheduler, as seen from inside that invocation.
 *
 * <p>An interceptor of a scheduled method cannot otherwise tell a call the scheduler made from a call the
 * application made directly, nor, for a method with several schedules, which of them triggered the call. The
 * scheduler invokes the method with this element in the {@link PropagatedContext}, so {@link #current()} answers
 * the method and the schedule that triggered it for the whole of the call, work the call propagates to other
 * threads or reactive continuations included.</p>
 *
 * @param method   The scheduled method
 * @param schedule The {@link Scheduled} annotation that triggered the invocation
 * @author Denis Stepanov
 * @since 5.3.0
 */
public record ScheduledExecution(ExecutableMethod<?, ?> method,
                                 AnnotationValue<Scheduled> schedule) implements PropagatedContextElement {

    /**
     * The scheduled invocation of the current propagated context.
     *
     * <p>It covers the whole of the invocation, so a method the scheduled method calls sees it as well; compare
     * {@link #method()} with the method being invoked to tell the two apart.</p>
     *
     * @return The scheduled invocation, or empty when the scheduler is not invoking a method in this context
     */
    public static Optional<ScheduledExecution> current() {
        return PropagatedContext.find().flatMap(ScheduledExecution::find);
    }

    /**
     * Finds the scheduled invocation of a propagated context.
     *
     * @param context The propagated context
     * @return The scheduled invocation, or empty when the context holds none
     */
    public static Optional<ScheduledExecution> find(PropagatedContext context) {
        return context.find(ScheduledExecution.class);
    }
}
