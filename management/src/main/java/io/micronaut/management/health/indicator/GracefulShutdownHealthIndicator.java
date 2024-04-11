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
package io.micronaut.management.health.indicator;


import io.micronaut.core.annotation.Internal;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.annotation.Readiness;
import io.micronaut.runtime.server.GracefulShutdownLifecycle;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Health indicator that goes DOWN when a graceful shutdown is initiated.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Singleton
@Readiness
@Internal
final class GracefulShutdownHealthIndicator implements HealthIndicator, GracefulShutdownLifecycle {
    private volatile boolean shuttingDown = false;

    @Override
    public Publisher<HealthResult> getResult() {
        return Mono.just(HealthResult.builder("gracefulShutdown")
            .status(shuttingDown ? HealthStatus.DOWN : HealthStatus.UP)
            .build());
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        shuttingDown = true;
        return CompletableFuture.completedFuture(null);
    }
}
