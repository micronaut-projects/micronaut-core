/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.cron.CronExpression;

/**
 * Represents the next fire time for a cron expression.
 *
 * @author croudet
 * @since 1.2.1
 */
@Internal
final class NextFireTime implements Supplier<Duration> {
    private Duration duration;
    private ZonedDateTime nextFireTime;
    private final CronExpression cron;

    /**
     * Default constructor.
     *
     * @param cron A cron expression
     */
    NextFireTime(CronExpression cron) {
        this.cron = cron;
        nextFireTime = ZonedDateTime.now();
    }

    @Override
    public Duration get() {
        ZonedDateTime now = ZonedDateTime.now();
        // check if the task have fired too early
        computeNextFireTime(now.isAfter(nextFireTime) ? now : nextFireTime);
        return duration;
    }

    private void computeNextFireTime(ZonedDateTime currentFireTime) {
        nextFireTime = cron.nextTimeAfter(currentFireTime);
        duration = Duration.ofMillis(nextFireTime.toInstant().toEpochMilli() - ZonedDateTime.now().toInstant().toEpochMilli());
    }
}
