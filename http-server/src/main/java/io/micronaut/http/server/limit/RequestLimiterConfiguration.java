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
package io.micronaut.http.server.limit;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.server.HttpServerConfiguration;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * A request limiter configured under {@code micronaut.server.limits.<name>}. Nothing is limited
 * unless a limiter is configured.
 *
 * <pre>{@code
 * micronaut.server.limits.api.type=rate
 * micronaut.server.limits.api.permits=100
 * micronaut.server.limits.api.period=1s
 * micronaut.server.limits.api.patterns[0]=/api/**
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@EachProperty(RequestLimiterConfiguration.PREFIX)
@Experimental
public class RequestLimiterConfiguration {

    /**
     * The prefix of the limiters.
     */
    public static final String PREFIX = HttpServerConfiguration.PREFIX + ".limits";

    private final String name;
    private Type type = Type.CONCURRENCY;
    private int maxConcurrent = 0;
    private long permits = 0;
    private Duration period = Duration.ofSeconds(1);
    private @Nullable Long burst;
    private @Nullable Duration retryAfter;
    private List<String> patterns = List.of();

    /**
     * @param name The name of the limiter
     */
    public RequestLimiterConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The name of the limiter
     */
    public String getName() {
        return name;
    }

    /**
     * @return The kind of limiter. Default {@code concurrency}.
     */
    public Type getType() {
        return type;
    }

    /**
     * @param type The kind of limiter. Default {@code concurrency}.
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * @return The maximum number of requests in flight of a {@code concurrency} limiter
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    /**
     * @param maxConcurrent The maximum number of requests in flight of a {@code concurrency} limiter
     */
    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    /**
     * @return The number of requests per {@link #getPeriod() period} of a {@code rate} limiter
     */
    public long getPermits() {
        return permits;
    }

    /**
     * @param permits The number of requests per {@link #getPeriod() period} of a {@code rate} limiter
     */
    public void setPermits(long permits) {
        this.permits = permits;
    }

    /**
     * @return The period of a {@code rate} limiter. Default one second.
     */
    public Duration getPeriod() {
        return period;
    }

    /**
     * @param period The period of a {@code rate} limiter. Default one second.
     */
    public void setPeriod(Duration period) {
        this.period = period;
    }

    /**
     * @return The number of requests a {@code rate} limiter lets through at once after a quiet
     * time. Defaults to {@link #getPermits() permits}.
     */
    public long getBurst() {
        return burst == null ? permits : burst;
    }

    /**
     * @param burst The number of requests a {@code rate} limiter lets through at once after a
     *              quiet time. Defaults to {@link #getPermits() permits}.
     */
    public void setBurst(long burst) {
        this.burst = burst;
    }

    /**
     * @return The {@code Retry-After} hint of a rejection of a {@code concurrency} limiter, or
     * {@code null} for none. A {@code rate} limiter computes it.
     */
    public @Nullable Duration getRetryAfter() {
        return retryAfter;
    }

    /**
     * @param retryAfter The {@code Retry-After} hint of a rejection of a {@code concurrency} limiter
     */
    public void setRetryAfter(@Nullable Duration retryAfter) {
        this.retryAfter = retryAfter;
    }

    /**
     * @return The path patterns, in the Ant style, of the requests the limiter applies to. Empty
     * by default: the limiter then only applies to the routes that select it with
     * {@link RequestLimit}. Use {@code /**} for every request.
     */
    public List<String> getPatterns() {
        return patterns;
    }

    /**
     * @param patterns The path patterns of the requests the limiter applies to
     */
    public void setPatterns(List<String> patterns) {
        this.patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }

    /**
     * The kind of limiter.
     */
    public enum Type {
        /**
         * A maximum number of requests in flight, see {@link ConcurrencyRequestLimiter}.
         */
        CONCURRENCY,
        /**
         * A token bucket, see {@link RateRequestLimiter}.
         */
        RATE
    }
}
