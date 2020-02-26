/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.instrument;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ScheduledExecutorService} that has been instrumented.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InstrumentedScheduledExecutorService extends InstrumentedExecutorService, ScheduledExecutorService {

    @Override
    ScheduledExecutorService getTarget();

    @Override
    default ScheduledFuture<?> schedule(@Nonnull Runnable command, long delay, @Nonnull TimeUnit unit) {
        return getTarget().schedule(instrument(command), delay, unit);
    }

    @Override
    default <V> ScheduledFuture<V> schedule(@Nonnull Callable<V> callable, long delay, @Nonnull TimeUnit unit) {
        return getTarget().schedule(instrument(callable), delay, unit);
    }

    @Override
    default ScheduledFuture<?> scheduleAtFixedRate(@Nonnull Runnable command, long initialDelay, long period, @Nonnull TimeUnit unit) {
        return getTarget().scheduleAtFixedRate(instrument(command), initialDelay, period, unit);
    }

    @Override
    default ScheduledFuture<?> scheduleWithFixedDelay(@Nonnull Runnable command, long initialDelay, long delay, @Nonnull TimeUnit unit) {
        return getTarget().scheduleWithFixedDelay(instrument(command), initialDelay, delay, unit);
    }
}
