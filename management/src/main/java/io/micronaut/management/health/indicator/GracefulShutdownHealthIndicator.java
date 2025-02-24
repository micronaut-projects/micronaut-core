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


import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.annotation.Readiness;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.runtime.graceful.GracefulShutdownManager;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Health indicator that goes DOWN when a graceful shutdown is initiated.
 *
 * @author Jonas Konrad
 * @since 4.9.0
 */
@Singleton
@Readiness
@Internal
final class GracefulShutdownHealthIndicator implements HealthIndicator, GracefulShutdownCapable {
    private static final String NAME = "gracefulShutdown";

    private final BeanProvider<GracefulShutdownManager> manager;
    private volatile boolean shuttingDown = false;

    GracefulShutdownHealthIndicator(BeanProvider<GracefulShutdownManager> manager) {
        this.manager = manager;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        HealthResult.Builder builder = HealthResult.builder(NAME)
            .status(shuttingDown ? HealthStatus.DOWN : HealthStatus.UP);
        manager.get().reportActiveTasks().ifPresent(n -> builder.details(Map.of("activeTasks", n)));
        return Mono.just(builder.build());
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        shuttingDown = true;
        return CompletableFuture.completedFuture(null);
    }
}
