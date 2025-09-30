/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.runtime.graceful;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * Helper class that can be used to call all {@link GracefulShutdownCapable} beans.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Singleton
@Requires(classes = GracefulShutdownCapable.class)
@Experimental
public final class GracefulShutdownManager {
    private final List<GracefulShutdownCapable> delegates;

    GracefulShutdownManager(List<GracefulShutdownCapable> delegates) {
        this.delegates = delegates;
    }

    /**
     * Shut down all {@link GracefulShutdownCapable} beans. Semantics of this method are like
     * {@link GracefulShutdownCapable#shutdownGracefully()}.
     *
     * @return A future that completes when all {@link GracefulShutdownCapable} beans have shut
     * down
     */
    @NonNull
    public CompletionStage<?> shutdownGracefully() {
        return GracefulShutdownCapable.shutdownAll(delegates.stream());
    }

    /**
     * Report the {@link GracefulShutdownCapable#reportActiveTasks() shutdown state} of all
     * capable beans.
     *
     * @return The combined number of active tasks
     */
    @NonNull
    public OptionalLong reportActiveTasks() {
        return GracefulShutdownCapable.combineActiveTasks(delegates);
    }
}
