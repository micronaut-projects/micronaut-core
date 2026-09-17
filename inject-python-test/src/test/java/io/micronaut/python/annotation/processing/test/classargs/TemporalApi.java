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
package io.micronaut.python.annotation.processing.test.classargs;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Java API returning and accepting java.time values.
 */
public final class TemporalApi {

    public static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 7, 21, 12, 34, 56, 123000000);

    private TemporalApi() {
    }

    public static LocalDateTime dateTime() {
        return DATE_TIME;
    }

    public static LocalDate date() {
        return DATE_TIME.toLocalDate();
    }

    public static LocalTime time() {
        return DATE_TIME.toLocalTime();
    }

    public static Instant instant() {
        return DATE_TIME.toInstant(ZoneOffset.UTC);
    }

    public static ZonedDateTime zoned() {
        return DATE_TIME.atZone(ZoneOffset.UTC);
    }

    public static Duration duration() {
        return Duration.ofMinutes(90);
    }

    public static String describe(LocalDateTime value) {
        return "datetime:" + value;
    }

    public static String describe(LocalDate value) {
        return "date:" + value;
    }

    public static String describe(Instant value) {
        return "instant:" + value;
    }

    public static String describe(Duration value) {
        return "duration:" + value;
    }
}
