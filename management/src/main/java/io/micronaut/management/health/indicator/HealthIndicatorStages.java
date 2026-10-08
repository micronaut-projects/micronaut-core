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
package io.micronaut.management.health.indicator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Calls {@link HealthIndicator#getResultAsync()} for the framework. An indicator that returns no
 * stage, or a stage completed with {@code null}, as a mock that only stubs
 * {@link HealthIndicator#getResult()} does, is called through {@link HealthIndicator#getResult()}.
 * An indicator that throws completes the stage with its error.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class HealthIndicatorStages {

    private HealthIndicatorStages() {
    }

    /**
     * @param indicator The indicator
     * @return The stage of {@link HealthIndicator#getResultAsync()}
     */
    public static CompletionStage<List<HealthResult>> getResult(HealthIndicator indicator) {
        try {
            return CompletionStagePublishers.orElseIfNull(
                indicator.getResultAsync(),
                () -> CompletionStagePublishers.collect(indicator.getResult())
            );
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
