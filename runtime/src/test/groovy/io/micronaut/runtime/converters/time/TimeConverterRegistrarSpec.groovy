/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.runtime.converters.time

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

class TimeConverterRegistrarSpec extends Specification {

    @Unroll
    void "test convert duration #val"() {
        given:
        ConversionService conversionService = new DefaultConversionService()
        new TimeConverterRegistrar().register(conversionService)


        expect:
        conversionService.convert(val, Duration).get() == expected

        where:
        val    | expected
        '10ms' | Duration.ofMillis(10)
        '10s'  | Duration.ofSeconds(10)
        '10m'  | Duration.ofMinutes(10)
        '10d'  | Duration.ofDays(10)
        '10h'  | Duration.ofHours(10)
        '10ns' | Duration.ofNanos(10)

    }

    @Unroll
    void "test convert default formats #val"() {
        given:
        ConversionService conversionService = new DefaultConversionService()
        new TimeConverterRegistrar().register(conversionService)

        expect:
        conversionService.convert(val1, val2).get() == expected

        where:
        val1                        | val2              | expected
        "2020-01-01"                | LocalDate         | LocalDate.of(2020, 1, 1)
        "2020-02-03T11:15:30"       | LocalDateTime     | LocalDateTime.parse("2020-02-03T11:15:30")
        "2020-12-03T10:15:30+01:00" | ZonedDateTime     | ZonedDateTime.parse("2020-12-03T10:15:30+01:00")
        "2011-12-03T10:15:30+01:00" | OffsetDateTime    | OffsetDateTime.parse("2011-12-03T10:15:30+01:00")
    }

}
