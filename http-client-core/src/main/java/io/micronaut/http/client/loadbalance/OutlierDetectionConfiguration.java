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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Experimental;

import java.time.Duration;

/**
 * Outlier detection of a load balanced service: an instance whose exchanges keep failing is
 * ejected, i.e. not selected, for a while, and tried again afterwards. The clients report the
 * outcome of every exchange to the {@link io.micronaut.http.client.LoadBalancer}, which counts
 * the consecutive failures of each instance.
 * <ul>
 *     <li>Transport failures always count: the connection could not be opened, the response did
 *     not arrive in time, or the connection or the stream was reset before the response was
 *     complete. {@link #getConsecutiveFailures()} of them in a row eject the instance.</li>
 *     <li>Responses with a status of 500 or above count only when
 *     {@link #getConsecutiveServerErrors()} is positive, since a server error may be the fault of
 *     one request rather than of the instance.</li>
 *     <li>A success resets both counts.</li>
 *     <li>An instance is ejected for {@link #getBaseEjectionTime()}, multiplied by the number of
 *     times it was ejected, up to {@link #getMaxEjectionTime()}; the multiplier is reset by a
 *     success after it was tried again.</li>
 *     <li>No more than {@link #getMaxEjectionPercent()} of the instances of the service are
 *     ejected at once, and when every instance that is up is ejected, the instances are selected
 *     as if none were.</li>
 * </ul>
 * Configured per service under {@code micronaut.http.services.<id>.outlier-detection}, and
 * disabled by default.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public class OutlierDetectionConfiguration {
    /**
     * The configuration prefix, relative to the service configuration.
     */
    public static final String PREFIX = "outlier-detection";
    /**
     * The default number of consecutive transport failures that eject an instance.
     */
    public static final int DEFAULT_CONSECUTIVE_FAILURES = 5;
    /**
     * The default base ejection time in seconds.
     */
    public static final long DEFAULT_BASE_EJECTION_TIME_SECONDS = 30;
    /**
     * The default maximum ejection time in seconds.
     */
    public static final long DEFAULT_MAX_EJECTION_TIME_SECONDS = 300;
    /**
     * The default maximum percentage of ejected instances.
     */
    public static final int DEFAULT_MAX_EJECTION_PERCENT = 50;

    private boolean enabled = false;
    private int consecutiveFailures = DEFAULT_CONSECUTIVE_FAILURES;
    private int consecutiveServerErrors = 0;
    private Duration baseEjectionTime = Duration.ofSeconds(DEFAULT_BASE_EJECTION_TIME_SECONDS);
    private Duration maxEjectionTime = Duration.ofSeconds(DEFAULT_MAX_EJECTION_TIME_SECONDS);
    private int maxEjectionPercent = DEFAULT_MAX_EJECTION_PERCENT;

    /**
     * @return Whether outlier detection is enabled. Default value {@code false}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether outlier detection is enabled. Default value {@code false}.
     *
     * @param enabled Whether outlier detection is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The number of consecutive transport failures that eject an instance
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Sets the number of consecutive transport failures that eject an instance. Default value
     * ({@value #DEFAULT_CONSECUTIVE_FAILURES}).
     *
     * @param consecutiveFailures The number of consecutive failures, at least {@code 1}
     */
    public void setConsecutiveFailures(int consecutiveFailures) {
        if (consecutiveFailures < 1) {
            throw new IllegalArgumentException("consecutiveFailures must be at least 1");
        }
        this.consecutiveFailures = consecutiveFailures;
    }

    /**
     * @return The number of consecutive responses with a status of 500 or above that eject an
     * instance, or {@code 0} if such responses do not count
     */
    public int getConsecutiveServerErrors() {
        return consecutiveServerErrors;
    }

    /**
     * Sets the number of consecutive responses with a status of 500 or above that eject an
     * instance. Default value {@code 0}: such responses do not count.
     *
     * @param consecutiveServerErrors The number of consecutive server errors, or {@code 0}
     */
    public void setConsecutiveServerErrors(int consecutiveServerErrors) {
        if (consecutiveServerErrors < 0) {
            throw new IllegalArgumentException("consecutiveServerErrors must not be negative");
        }
        this.consecutiveServerErrors = consecutiveServerErrors;
    }

    /**
     * @return How long an instance is ejected the first time
     */
    public Duration getBaseEjectionTime() {
        return baseEjectionTime;
    }

    /**
     * Sets how long an instance is ejected the first time; each further ejection lasts one more
     * base time, up to the maximum. Default value ({@value #DEFAULT_BASE_EJECTION_TIME_SECONDS}
     * seconds).
     *
     * @param baseEjectionTime The base ejection time, positive
     */
    public void setBaseEjectionTime(Duration baseEjectionTime) {
        if (baseEjectionTime == null || baseEjectionTime.isNegative() || baseEjectionTime.isZero()) {
            throw new IllegalArgumentException("baseEjectionTime must be positive");
        }
        this.baseEjectionTime = baseEjectionTime;
    }

    /**
     * @return The longest an instance is ejected at once
     */
    public Duration getMaxEjectionTime() {
        return maxEjectionTime;
    }

    /**
     * Sets the longest an instance is ejected at once. Default value
     * ({@value #DEFAULT_MAX_EJECTION_TIME_SECONDS} seconds).
     *
     * @param maxEjectionTime The maximum ejection time, positive
     */
    public void setMaxEjectionTime(Duration maxEjectionTime) {
        if (maxEjectionTime == null || maxEjectionTime.isNegative() || maxEjectionTime.isZero()) {
            throw new IllegalArgumentException("maxEjectionTime must be positive");
        }
        this.maxEjectionTime = maxEjectionTime;
    }

    /**
     * @return The maximum percentage of the instances of the service that are ejected at once
     */
    public int getMaxEjectionPercent() {
        return maxEjectionPercent;
    }

    /**
     * Sets the maximum percentage of the instances of the service that are ejected at once: an
     * instance is not ejected when that would eject more. Default value
     * ({@value #DEFAULT_MAX_EJECTION_PERCENT}).
     *
     * @param maxEjectionPercent The maximum percentage, between {@code 0} and {@code 100}
     */
    public void setMaxEjectionPercent(int maxEjectionPercent) {
        if (maxEjectionPercent < 0 || maxEjectionPercent > 100) {
            throw new IllegalArgumentException("maxEjectionPercent must be between 0 and 100");
        }
        this.maxEjectionPercent = maxEjectionPercent;
    }
}
