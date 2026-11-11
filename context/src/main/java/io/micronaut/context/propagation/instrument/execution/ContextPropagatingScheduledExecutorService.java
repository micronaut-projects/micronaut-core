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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wraps {@link ScheduledExecutorService} to instrument it for propagating the {@link PropagatedContext}
 * across threads.
 *
 * <p>Unless it is given a {@link PropagatedContext}, periodic tasks ({@link #scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
 * and {@link #scheduleWithFixedDelay(Runnable, long, long, TimeUnit)}) don't capture the context of the scheduling thread,
 * because they would keep it alive until they are cancelled.</p>
 *
 * @author Hauke Lange
 * @since 4.9.0
 */
@Internal
public class ContextPropagatingScheduledExecutorService extends ContextPropagatingExecutorService implements InstrumentedScheduledExecutorService {
    private final ScheduledExecutorService target;

    @Nullable
    private final PropagatedContext propagatedContext;

    public ContextPropagatingScheduledExecutorService(ScheduledExecutorService target) {
        this(target, null);
    }

    public ContextPropagatingScheduledExecutorService(
        ScheduledExecutorService target,
        @Nullable PropagatedContext propagatedContext
    ) {
        super(target, propagatedContext);
        this.target = target;
        this.propagatedContext = propagatedContext;
    }

    @Override
    public ScheduledExecutorService getTarget() {
        return this.target;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return target.scheduleAtFixedRate(instrumentPeriodic(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return target.scheduleWithFixedDelay(instrumentPeriodic(command), initialDelay, delay, unit);
    }

    private Runnable instrumentPeriodic(Runnable command) {
        return propagatedContext != null ? propagatedContext.wrap(command) : command;
    }
}
