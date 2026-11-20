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
package io.micronaut.core.convert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A request-supplied string of millions of digits must not cost quadratic CPU in the String to
 * BigDecimal/BigInteger converters (form fields are converted on the event loop).
 */
class BigNumberConversionLengthTest {

    private static final String DIGITS = "7".repeat(1_500_000);

    @Test
    void hugeBigDecimalIsRejectedOrConvertedQuickly() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ConversionService.SHARED.convert(DIGITS, BigDecimal.class));
    }

    @Test
    void hugeBigIntegerIsRejectedOrConvertedQuickly() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ConversionService.SHARED.convert(DIGITS, BigInteger.class));
    }
}
