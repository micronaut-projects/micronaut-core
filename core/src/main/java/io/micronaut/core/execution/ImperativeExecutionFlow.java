/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

/**
 * The imperative execution flow.
 *
 * @param <T> The value type
 * @author Denis Stepnov
 * @since 4.0.0
 */
@Internal
public interface ImperativeExecutionFlow<T> extends ExecutionFlow<T> {

    /**
     * @return The value if present
     */
    @Nullable
    T getValue();

    /**
     * @return The exception if present
     */
    @Nullable
    Throwable getError();

    /**
     * @return The context if present
     */
    @NonNull
    Map<String, Object> getContext();

    @NonNull
    @Override
    default ImperativeExecutionFlow<T> tryComplete() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This method has no effect for {@link ImperativeExecutionFlow}, it makes no sense
     * to use it
     */
    @Override
    @NonNull
    @Deprecated
    default ExecutionFlow<T> timeout(@NonNull Duration timeout, @NonNull ScheduledExecutorService scheduler, @Nullable BiConsumer<T, Throwable> onDiscard) {
        return this;
    }
}
