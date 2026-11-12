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
package io.micronaut.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A media type with a q parameter of millions of digits (for example a multipart part header)
 * must be rejected or parsed in bounded time, not with quadratic BigDecimal parsing.
 */
class MediaTypeQualityLengthTest {

    @Test
    void hugeQualityValueDoesNotBlock() {
        String value = "text/plain;q=" + "1".repeat(1_500_000);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            try {
                MediaType.of(value).getQualityAsNumber();
            } catch (RuntimeException e) {
                // rejecting the value is fine
            }
        });
    }
}
