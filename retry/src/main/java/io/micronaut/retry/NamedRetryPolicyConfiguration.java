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
import io.micronaut.core.reflect.ClassUtils;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The configuration of a named retry policy of the {@link RetryRegistry}, e.g.
 * <pre>
 * micronaut:
 *   retry:
 *     policies:
 *       orders:
 *         attempts: 5
 *         delay: 200ms
 *         multiplier: 2
 *         max-delay: 5s
 *         jitter: 0.1
 *         includes: java.io.IOException
 * </pre>
 * The policy captures any {@link Exception}, as {@code @Retryable} does, so that a checked
 * exception of the includes is retried.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@EachProperty(NamedRetryPolicyConfiguration.PREFIX)
public class NamedRetryPolicyConfiguration {

    /**
     * The prefix of the configurations.
     */
    public static final String PREFIX = "micronaut.retry.policies";

    private final String name;
    private int attempts = RetryPolicy.DEFAULT_MAX_ATTEMPTS;
    private Duration delay = RetryPolicy.DEFAULT_DELAY;
    @Nullable
    private Duration maxDelay;
    private double multiplier = RetryPolicy.DEFAULT_MULTIPLIER;
    private double jitter = RetryPolicy.DEFAULT_JITTER;
    private List<String> includes = new ArrayList<>();
    private List<String> excludes = new ArrayList<>();

    /**
     * @param name The name of the retry policy
     */
    public NamedRetryPolicyConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The name of the retry policy
     */
    public String getName() {
        return name;
    }

    /**
     * @return The maximum number of retries, after the first call
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * @param attempts The maximum number of retries, after the first call, default 3; e.g. 1
     *                 calls the method at most twice
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
     * @param delay The delay between attempts, default 1s
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
     * @param maxDelay The maximum overall delay, default none
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
     * @param multiplier The multiplier of the delay, default 1.0
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
     * @param jitter The jitter factor of the delay, between 0 and 1, default 0
     */
    public void setJitter(double jitter) {
        this.jitter = jitter;
    }

    /**
     * @return The fully qualified names of the exception types to retry, all if empty
     */
    public List<String> getIncludes() {
        return includes;
    }

    /**
     * The exception types to retry, by fully qualified name. A name that is not a class on the
     * classpath fails {@link #toPolicy()}, rather than being dropped.
     *
     * @param includes The names of the exception types to retry, default all
     */
    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    /**
     * @return The fully qualified names of the exception types not to retry
     */
    public List<String> getExcludes() {
        return excludes;
    }

    /**
     * The exception types not to retry, by fully qualified name. A name that is not a class on
     * the classpath fails {@link #toPolicy()}, rather than being dropped.
     *
     * @param excludes The names of the exception types not to retry, default none
     */
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    /**
     * @return The policy of the configuration
     * @throws IllegalArgumentException if a value is invalid, e.g. an include that is not the
     * name of a {@link Throwable} class
     */
    public RetryPolicy toPolicy() {
        try {
            return RetryPolicy.builder()
                .maxAttempts(attempts)
                .delay(delay)
                .maxDelay(maxDelay)
                .multiplier(multiplier)
                .jitter(jitter)
                .capturedException(Exception.class)
                .includes(throwables(includes, "includes"))
                .excludes(throwables(excludes, "excludes"))
                .build();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid retry policy [" + name + "] of " + PREFIX + "." + name + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable>[] throwables(List<String> typeNames, String member) {
        ClassLoader classLoader = NamedRetryPolicyConfiguration.class.getClassLoader();
        Class<? extends Throwable>[] result = new Class[typeNames.size()];
        for (int i = 0; i < result.length; i++) {
            String typeName = typeNames.get(i).strip();
            Class<?> type = ClassUtils.forName(typeName, classLoader).orElseThrow(() ->
                new IllegalArgumentException(member + " must be exception types, class not found: " + typeName)
            );
            if (!Throwable.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(member + " must be exception types, got " + type.getName());
            }
            result[i] = (Class<? extends Throwable>) type;
        }
        return result;
    }
}
