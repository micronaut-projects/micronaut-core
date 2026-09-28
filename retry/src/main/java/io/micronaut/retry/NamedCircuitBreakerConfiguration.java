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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The configuration of a named circuit breaker of the {@link CircuitBreakerRegistry}, e.g.
 * <pre>
 * micronaut:
 *   retry:
 *     circuit-breakers:
 *       orders:
 *         reset: 30s
 *         attempts: 1
 * </pre>
 * The retries capture any {@link Exception}, as those of {@code @CircuitBreaker} do, so that a
 * checked exception is retried and opens the circuit.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@EachProperty(NamedCircuitBreakerConfiguration.PREFIX)
public class NamedCircuitBreakerConfiguration {

    /**
     * The prefix of the configurations.
     */
    public static final String PREFIX = "micronaut.retry.circuit-breakers";

    private final String name;
    private int attempts = RetryPolicy.DEFAULT_MAX_ATTEMPTS;
    private Duration delay = CircuitBreakerPolicy.DEFAULT_DELAY;
    @Nullable
    private Duration maxDelay = CircuitBreakerPolicy.DEFAULT_MAX_DELAY;
    private double multiplier = CircuitBreakerPolicy.DEFAULT_MULTIPLIER;
    private double jitter = RetryPolicy.DEFAULT_JITTER;
    private Duration reset = CircuitBreakerPolicy.DEFAULT_RESET_TIMEOUT;
    private boolean throwWrappedException;
    @Nullable
    private Integer requestVolumeThreshold;
    @Nullable
    private Double failureRatio;
    @Nullable
    private Integer successThreshold;
    private List<Class<? extends Throwable>> failOn = new ArrayList<>();
    private List<Class<? extends Throwable>> skipOn = new ArrayList<>();

    /**
     * @param name The name of the circuit breaker
     */
    public NamedCircuitBreakerConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The name of the circuit breaker
     */
    public String getName() {
        return name;
    }

    /**
     * @return The maximum number of attempts of an operation of {@link CircuitBreakerRegistry#circuitBreaker(String)}
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * @param attempts The maximum number of attempts, default 3
     */
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    /**
     * @return The delay between attempts
     */
    public Duration getDelay() {
        return delay;
    }

    /**
     * @param delay The delay between attempts, default 500ms
     */
    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    /**
     * @return The maximum overall delay
     */
    public @Nullable Duration getMaxDelay() {
        return maxDelay;
    }

    /**
     * @param maxDelay The maximum overall delay, default 5s
     */
    public void setMaxDelay(@Nullable Duration maxDelay) {
        this.maxDelay = maxDelay;
    }

    /**
     * @return The multiplier of the delay
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * @param multiplier The multiplier of the delay, default 0
     */
    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    /**
     * @return The jitter factor of the delay
     */
    public double getJitter() {
        return jitter;
    }

    /**
     * @param jitter The jitter factor of the delay, default 0
     */
    public void setJitter(double jitter) {
        this.jitter = jitter;
    }

    /**
     * @return The time the circuit stays open before it half-opens
     */
    public Duration getReset() {
        return reset;
    }

    /**
     * @param reset The time the circuit stays open before it half-opens, default 20s
     */
    public void setReset(Duration reset) {
        this.reset = reset;
    }

    /**
     * @return Whether the error of an open circuit is wrapped in a {@link io.micronaut.retry.exception.CircuitOpenException}
     */
    public boolean isThrowWrappedException() {
        return throwWrappedException;
    }

    /**
     * @param throwWrappedException Whether the error of an open circuit is wrapped, default false
     */
    public void setThrowWrappedException(boolean throwWrappedException) {
        this.throwWrappedException = throwWrappedException;
    }

    /**
     * @return The size of the rolling window, if the circuit has one
     * @since 5.3.0
     */
    public @Nullable Integer getRequestVolumeThreshold() {
        return requestVolumeThreshold;
    }

    /**
     * @param requestVolumeThreshold The number of calls of the rolling window, see
     *                               {@link CircuitBreakerPolicy.Window}; setting it, or another
     *                               setting of the window, gives the circuit a rolling window
     * @since 5.3.0
     */
    public void setRequestVolumeThreshold(@Nullable Integer requestVolumeThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
    }

    /**
     * @return The ratio of failures that opens the circuit
     * @since 5.3.0
     */
    public @Nullable Double getFailureRatio() {
        return failureRatio;
    }

    /**
     * @param failureRatio The ratio of failures of a full window that opens the circuit, default 0.5
     * @since 5.3.0
     */
    public void setFailureRatio(@Nullable Double failureRatio) {
        this.failureRatio = failureRatio;
    }

    /**
     * @return The number of trial calls of a half-open circuit
     * @since 5.3.0
     */
    public @Nullable Integer getSuccessThreshold() {
        return successThreshold;
    }

    /**
     * @param successThreshold The number of trial calls of a half-open circuit, which all succeed
     *                         to close it, default 1
     * @since 5.3.0
     */
    public void setSuccessThreshold(@Nullable Integer successThreshold) {
        this.successThreshold = successThreshold;
    }

    /**
     * @return The exceptions that count as a failure
     * @since 5.3.0
     */
    public List<Class<? extends Throwable>> getFailOn() {
        return failOn;
    }

    /**
     * @param failOn The exceptions that count as a failure, default every one
     * @since 5.3.0
     */
    public void setFailOn(List<Class<? extends Throwable>> failOn) {
        this.failOn = failOn;
    }

    /**
     * @return The exceptions that count as a success
     * @since 5.3.0
     */
    public List<Class<? extends Throwable>> getSkipOn() {
        return skipOn;
    }

    /**
     * @param skipOn The exceptions that count as a success, whatever failOn says
     * @since 5.3.0
     */
    public void setSkipOn(List<Class<? extends Throwable>> skipOn) {
        this.skipOn = skipOn;
    }

    /**
     * @return The policy of the configuration
     */
    public CircuitBreakerPolicy toPolicy() {
        CircuitBreakerPolicy.Builder builder = CircuitBreakerPolicy.builder()
            .maxAttempts(attempts)
            .delay(delay)
            .multiplier(multiplier)
            .jitter(jitter)
            .resetTimeout(reset)
            .capturedException(Exception.class)
            .throwWrappedException(throwWrappedException);
        if (maxDelay != null) {
            builder.maxDelay(maxDelay);
        }
        if (requestVolumeThreshold != null) {
            builder.requestVolumeThreshold(requestVolumeThreshold);
        }
        if (failureRatio != null) {
            builder.failureRatio(failureRatio);
        }
        if (successThreshold != null) {
            builder.successThreshold(successThreshold);
        }
        if (!failOn.isEmpty()) {
            builder.failOn(failOn.toArray(new Class[0]));
        }
        if (!skipOn.isEmpty()) {
            builder.skipOn(skipOn.toArray(new Class[0]));
        }
        return builder.build();
    }
}
