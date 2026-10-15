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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The rolling window of a circuit breaker, as in MicroProfile Fault Tolerance: the outcomes of
 * the last {@link #requestVolumeThreshold()} calls of a closed circuit decide when it opens.
 * <ul>
 *     <li>A closed circuit opens when its window is full and the ratio of failures in it
 *     reaches {@link #failureRatio()}; never before the window is full.</li>
 *     <li>An open circuit half-opens after the reset timeout of its {@link CircuitBreakerPolicy},
 *     and permits {@link #successThreshold()} trial calls: it closes when they all succeed, and
 *     any failure opens it again. A call beyond the permitted trials is rejected like a call of
 *     an open circuit, and counts for nothing.</li>
 *     <li>Every change of state starts a new window.</li>
 * </ul>
 * An exception counts as a success if it is one of {@link #skipOn()}, else as a failure if it
 * is one of {@link #failOn()}, else as a success.
 *
 * <p>A circuit without a window is the circuit breaker of Micronaut: the first failure that
 * survives the retries opens it, and the first success of a half-open circuit closes it.</p>
 *
 * @param requestVolumeThreshold The number of calls of the window, at least 1
 * @param failureRatio           The ratio of failures of a full window that opens the circuit,
 *                               between 0 and 1; a window opens it only with a failure
 * @param successThreshold       The number of trial calls of a half-open circuit, at least 1
 * @param failOn                 The exceptions that count as a failure; empty for every one
 * @param skipOn                 The exceptions that count as a success
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record CircuitBreakerWindow(int requestVolumeThreshold,
                                   double failureRatio,
                                   int successThreshold,
                                   List<Class<? extends Throwable>> failOn,
                                   List<Class<? extends Throwable>> skipOn) {

    /**
     * The default size of the window.
     */
    public static final int DEFAULT_REQUEST_VOLUME_THRESHOLD = 20;
    /**
     * The default ratio of failures.
     */
    public static final double DEFAULT_FAILURE_RATIO = 0.5;
    /**
     * The default number of trial calls.
     */
    public static final int DEFAULT_SUCCESS_THRESHOLD = 1;

    /**
     * @param requestVolumeThreshold The number of calls of the window
     * @param failureRatio           The ratio of failures
     * @param successThreshold       The number of trial calls
     * @param failOn                 The exceptions that count as a failure
     * @param skipOn                 The exceptions that count as a success
     */
    public CircuitBreakerWindow {
        if (requestVolumeThreshold < 1) {
            throw new IllegalArgumentException("requestVolumeThreshold must be at least 1");
        }
        if (failureRatio < 0 || failureRatio > 1 || Double.isNaN(failureRatio)) {
            throw new IllegalArgumentException("failureRatio must be between 0 and 1");
        }
        if (successThreshold < 1) {
            throw new IllegalArgumentException("successThreshold must be at least 1");
        }
        failOn = List.copyOf(Objects.requireNonNull(failOn, "failOn"));
        skipOn = List.copyOf(Objects.requireNonNull(skipOn, "skipOn"));
    }

    /**
     * Creates a builder of a window, with the defaults of every setting.
     *
     * @return The builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param failure An exception of a call
     * @return Whether it counts as a failure
     */
    public boolean isFailure(Throwable failure) {
        for (Class<? extends Throwable> type : skipOn) {
            if (type.isInstance(failure)) {
                return false;
            }
        }
        if (failOn.isEmpty()) {
            return true;
        }
        for (Class<? extends Throwable> type : failOn) {
            if (type.isInstance(failure)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The number of failures of a full window that opens the circuit, at least 1
     */
    public int failureThreshold() {
        return Math.max(1, (int) Math.ceil(failureRatio * requestVolumeThreshold - 1e-9));
    }

    /**
     * The builder of a {@link CircuitBreakerWindow}.
     */
    public static final class Builder {

        private int requestVolumeThreshold = DEFAULT_REQUEST_VOLUME_THRESHOLD;
        private double failureRatio = DEFAULT_FAILURE_RATIO;
        private int successThreshold = DEFAULT_SUCCESS_THRESHOLD;
        private final List<Class<? extends Throwable>> failOn = new ArrayList<>();
        private final List<Class<? extends Throwable>> skipOn = new ArrayList<>();

        private Builder() {
        }

        /**
         * @param requestVolumeThreshold The number of calls of the window, at least 1, default 20,
         *                               see {@link CircuitBreakerWindow#requestVolumeThreshold()}
         * @return This builder
         */
        public Builder requestVolumeThreshold(int requestVolumeThreshold) {
            this.requestVolumeThreshold = requestVolumeThreshold;
            return this;
        }

        /**
         * @param failureRatio The ratio of failures of a full window that opens the circuit,
         *                     between 0 and 1, default 0.5, see {@link CircuitBreakerWindow#failureRatio()}
         * @return This builder
         */
        public Builder failureRatio(double failureRatio) {
            this.failureRatio = failureRatio;
            return this;
        }

        /**
         * @param successThreshold The number of trial calls of a half-open circuit, which all
         *                         succeed to close it, at least 1, default 1, see
         *                         {@link CircuitBreakerWindow#successThreshold()}
         * @return This builder
         */
        public Builder successThreshold(int successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }

        /**
         * @param failOn The exceptions that count as a failure, default every one, see
         *               {@link CircuitBreakerWindow#failOn()}
         * @return This builder
         */
        @SafeVarargs
        public final Builder failOn(Class<? extends Throwable>... failOn) {
            this.failOn.addAll(List.of(failOn));
            return this;
        }

        /**
         * @param skipOn The exceptions that count as a success, see {@link CircuitBreakerWindow#skipOn()}
         * @return This builder
         */
        @SafeVarargs
        public final Builder skipOn(Class<? extends Throwable>... skipOn) {
            this.skipOn.addAll(List.of(skipOn));
            return this;
        }

        /**
         * @return The window
         * @throws IllegalArgumentException if a setting is out of its range
         */
        public CircuitBreakerWindow build() {
            return new CircuitBreakerWindow(requestVolumeThreshold, failureRatio, successThreshold, failOn, skipOn);
        }
    }
}
