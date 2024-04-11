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
import jakarta.inject.Singleton;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Helper class that can be used to call all {@link GracefulShutdownLifecycle} beans.
 *
 * @since 4.5.0
 * @author Jonas Konrad
 */
@Singleton
@Requires(classes = GracefulShutdownLifecycle.class)
public final class GracefulShutdownManager {
    private final List<GracefulShutdownLifecycle> delegates;

    GracefulShutdownManager(List<GracefulShutdownLifecycle> delegates) {
        this.delegates = delegates;
    }

    /**
     * Shut down all {@link GracefulShutdownLifecycle} beans. Semantics of this method are like
     * {@link GracefulShutdownLifecycle#shutdownGracefully()}.
     *
     * @return A future that completes when all {@link GracefulShutdownLifecycle} beans have shut
     * down
     */
    public CompletionStage<?> shutdownGracefully() {
        return GracefulShutdownLifecycle.shutdownAll(delegates.stream());
    }
}
