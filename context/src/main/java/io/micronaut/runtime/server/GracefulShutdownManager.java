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
package io.micronaut.runtime.server;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Helper class that can be used to call all {@link GracefulShutdownCapable} beans.
 *
 * @since 4.5.0
 * @author Jonas Konrad
 */
@Singleton
@Requires(classes = GracefulShutdownCapable.class)
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
     * Report the {@link GracefulShutdownCapable#reportShutdownState() shutdown state} of all
     * capable beans. This will return a {@link GracefulShutdownCapable.CombinedShutdownState}
     * where the key is the bean class name.
     *
     * @return The combined shutdown state
     */
    @NonNull
    public Optional<GracefulShutdownCapable.ShutdownState> reportShutdownState() {
        return GracefulShutdownCapable.CombinedShutdownState.combineShutdownState(
            delegates,
            d -> d.getClass().getSimpleName(),
            n -> Map.entry("other", new GracefulShutdownCapable.SingleShutdownState("And " + n + " other beans"))
        );
    }
}
