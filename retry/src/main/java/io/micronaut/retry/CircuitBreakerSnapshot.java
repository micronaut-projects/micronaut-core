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
package io.micronaut.retry;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The state and the counters of a named circuit breaker at one moment, see
 * {@link CircuitBreakerRegistry#findSnapshot(String)}.
 *
 * @param name                   The name of the circuit breaker
 * @param state                  The state: an open circuit whose reset timeout elapsed is half-open
 * @param requestVolumeThreshold The size of the rolling window, or {@code 0} for a circuit without one
 * @param calls                  The calls in the window of a closed circuit
 * @param failures               The failures in the window of a closed circuit
 * @param trials                 The trial calls a half-open circuit permitted
 * @param successes              The trial calls of a half-open circuit that succeeded
 * @param openedCount            The number of times the circuit opened
 * @param since                  When the circuit was created or last changed state, or {@code null} for a
 *                               configured circuit that was not used yet
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record CircuitBreakerSnapshot(String name,
                                     CircuitState state,
                                     int requestVolumeThreshold,
                                     int calls,
                                     int failures,
                                     int trials,
                                     int successes,
                                     long openedCount,
                                     @Nullable Instant since) {
}
