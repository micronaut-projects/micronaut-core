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
package io.micronaut.core.convert

import spock.lang.Specification

/**
 * Converting a string to BigDecimal/BigInteger costs quadratic time in its length, so a
 * request-supplied string of excessive length must be rejected.
 */
class BigNumberConversionLengthSpec extends Specification {

    void "an excessively long number is rejected"() {
        given:
        String digits = "7" * 5000

        expect:
        !ConversionService.SHARED.convert(digits, BigDecimal).isPresent()
        !ConversionService.SHARED.convert(digits, BigInteger).isPresent()
        ConversionService.SHARED.convert("7" * 100, BigDecimal).get() == new BigDecimal("7" * 100)
        ConversionService.SHARED.convert("7" * 100, BigInteger).get() == new BigInteger("7" * 100)
    }
}
