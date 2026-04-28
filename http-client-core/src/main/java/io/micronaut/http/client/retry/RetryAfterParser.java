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
package io.micronaut.http.client.retry;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Parses the {@code Retry-After} header per
 * <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after">RFC 9110 §10.2.3</a>,
 * supporting both the delta-seconds form and the HTTP-date form.
 *
 * @since 5.0.0
 */
@Internal
final class RetryAfterParser {

    private static final Pattern DELTA_SECONDS = Pattern.compile("\\d+");

    private RetryAfterParser() {
    }

    /**
     * Parses a {@code Retry-After} header value into a duration relative to the current instant.
     *
     * @param headerValue The raw header value, possibly {@code null}
     * @param clock       The clock used to compute deltas from HTTP-date values
     * @return The hint, or {@code null} if the header is absent or unparseable. Negative values
     *         are coerced to {@link Duration#ZERO}.
     */
    @Nullable
    static Duration parse(@Nullable String headerValue, Clock clock) {
        if (headerValue == null) {
            return null;
        }
        String trimmed = headerValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (DELTA_SECONDS.matcher(trimmed).matches()) {
            // delta-seconds form; only failure path is overflow on a huge digit string
            try {
                long seconds = Long.parseLong(trimmed);
                return seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        try {
            ZonedDateTime target = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            Duration delta = Duration.between(now, target);
            return delta.isNegative() ? Duration.ZERO : delta;
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }
}
