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
package io.micronaut.context.propagation.instrument.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * Wraps {@link ExecutorService} to instrument it for propagating the {@link PropagatedContext}
 * across threads.
 *
 * @author Hauke Lange
 * @since 4.9.0
 */
@Internal
public class ContextPropagatingExecutorService implements InstrumentedExecutorService {
    private final ExecutorService target;

    private final PropagatedContext propagatedContext;

    public ContextPropagatingExecutorService(ExecutorService target) {
        this(target, null);
    }

    public ContextPropagatingExecutorService(
        ExecutorService target,
        @Nullable PropagatedContext propagatedContext
    ) {
        this.target = target;
        this.propagatedContext = propagatedContext;
    }

    @Override
    public ExecutorService getTarget() {
        return target;
    }

    @Override
    public <T> Callable<T> instrument(Callable<T> task) {
        if (propagatedContext != null) {
            return propagatedContext.wrap(task);
        } else {
            return PropagatedContext.wrapCurrent(task);
        }
    }

    @Override
    public Runnable instrument(Runnable task) {
        if (propagatedContext != null) {
            return propagatedContext.wrap(task);
        } else {
            return PropagatedContext.wrapCurrent(task);
        }
    }

    /**
     * Unwraps the target {@link ExecutorService} from the given {@link InstrumentedExecutorService}
     * if it is instrumented with {@link ContextPropagatingExecutorService}.
     *
     * @param executorService The instrumented executor service
     * @return The target executor service of the {@link ContextPropagatingScheduledExecutorService}
     *  or empty if not found
     */
    public static Optional<ExecutorService> unwrap(ExecutorService executorService) {
        if (!(executorService instanceof InstrumentedExecutorService)) {
            return Optional.empty();
        }
        ExecutorService target = executorService;
        while (target instanceof InstrumentedExecutorService ies) {
            if (target instanceof ContextPropagatingExecutorService contextPropagatingExecutorService) {
                return Optional.of(contextPropagatingExecutorService.getTarget());
            }
            target = ies.getTarget();
        }
        return Optional.empty();
    }

    /**
     * Checks if the given {@link ExecutorService} is instrumented with
     * {@link ContextPropagatingExecutorService}.
     *
     * @param executorService The executor service to check
     * @return true if it is instrumented, false otherwise
     */
    public static boolean isInstrumented(ExecutorService executorService) {
        return executorService instanceof InstrumentedExecutorService ies && unwrap(ies).isPresent();
    }
}
