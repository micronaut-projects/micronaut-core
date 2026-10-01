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
package io.micronaut.http.server;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.ApplicationConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The warm-up of a training run ({@link ApplicationConfiguration#TRAINING_ENABLED}): once the
 * server has started, it sends GET requests to itself before the application stops. Only the
 * {@code start} mode, the default ({@link ApplicationConfiguration#TRAINING_MODE}), starts the
 * server; the {@code load} mode starts no server and ignores these settings.
 *
 * @since 5.3.0
 */
@ConfigurationProperties(TrainingWarmupConfiguration.PREFIX)
@Requires(property = ApplicationConfiguration.TRAINING_ENABLED, pattern = TrainingWarmupConfiguration.ENABLED_PATTERN)
public class TrainingWarmupConfiguration {

    /**
     * The prefix of the warm-up settings.
     */
    public static final String PREFIX = "micronaut.application.training.warmup";

    /**
     * The default number of times the warm-up requests every path.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_REPEAT = 1;

    /**
     * The values of {@link ApplicationConfiguration#TRAINING_ENABLED} that turn a training run on:
     * {@code true} in any case, as {@link io.micronaut.runtime.Micronaut#start()} reads it.
     */
    static final String ENABLED_PATTERN = "(?i)true";

    private List<String> paths = Collections.emptyList();
    private int repeat = DEFAULT_REPEAT;

    /**
     * @return The paths the warm-up requests
     */
    public List<String> getPaths() {
        return paths;
    }

    /**
     * The paths the warm-up sends a GET request to, in order, including the context path if the
     * server has one. A path may carry a query string. Default value: none, so no warm-up
     * requests are sent.
     *
     * @param paths The paths
     */
    public void setPaths(@Nullable List<String> paths) {
        this.paths = paths == null ? Collections.emptyList() : paths;
    }

    /**
     * @return How many times the warm-up requests every path
     */
    public int getRepeat() {
        return repeat;
    }

    /**
     * How many times the warm-up requests the paths. It must be at least 1. Default value ({@value #DEFAULT_REPEAT}).
     *
     * @param repeat The number of rounds
     * @throws IllegalArgumentException if the value is less than 1.
     */
    public void setRepeat(int repeat) {
        if (repeat < 1) {
            throw new IllegalArgumentException(PREFIX + ".repeat must be at least 1 but was " + repeat);
        }
        this.repeat = repeat;
    }
}
