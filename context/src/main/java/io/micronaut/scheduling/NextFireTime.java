/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.cron.CronExpression;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

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
    private final ZoneId zoneId;

    /**
     * Default constructor.
     *
     * @param cron A cron expression
     */
    NextFireTime(CronExpression cron) {
        this(cron, ZoneId.systemDefault());
    }

    /**
     * @param cron A cron expression
     * @param zoneId The zoneId to base the cron expression on
     */
    NextFireTime(CronExpression cron, ZoneId zoneId) {
        this.cron = cron;
        this.zoneId = zoneId;
        nextFireTime = ZonedDateTime.now(zoneId);
    }

    @Override
    public Duration get() {
        var now = ZonedDateTime.now(zoneId);
        // check if the task have fired too early
        computeNextFireTime(now.isAfter(nextFireTime) ? now : nextFireTime);
        return duration;
    }

    private void computeNextFireTime(ZonedDateTime currentFireTime) {
        nextFireTime = cron.nextTimeAfter(currentFireTime);
        duration = Duration.ofMillis(nextFireTime.toInstant().toEpochMilli() - ZonedDateTime.now(zoneId).toInstant().toEpochMilli());
    }
}
