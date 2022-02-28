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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    void "test convert #input to #typeName"() {
        given:
        ConversionService conversionService = new DefaultConversionService()
        new TimeConverterRegistrar().register(conversionService)

        when:
        def result = conversionService.convert(input, type).get();

        then:
        result == expected

        and:
        result.toString() == input

        where:
        input                                      | type           | expected
        '2021-11-26T21:19+01:00'                   | OffsetDateTime | OffsetDateTime.of(LocalDateTime.of(2021, 11, 26, 21, 19), ZoneOffset.ofHours(1))
        '21:19:32+01:00'                           | OffsetTime     | OffsetTime.of(LocalTime.of(21, 19, 32), ZoneOffset.ofHours(1))
        '21:19+01:00'                              | OffsetTime     | OffsetTime.of(LocalTime.of(21, 19), ZoneOffset.ofHours(1))
        '2021-11-26T21:19'                         | LocalDateTime  | LocalDateTime.of(2021, 11, 26, 21, 19)
        '2021-11-26'                               | LocalDate      | LocalDate.of(2021, 11, 26)
        '10:15:34'                                 | LocalTime      | LocalTime.of(10, 15, 34)
        '10:15'                                    | LocalTime      | LocalTime.of(10, 15)
        '2022-02-03T12:15:30+02:00[Europe/Athens]' | ZonedDateTime  | LocalDateTime.of(2022, 2, 3, 10, 15, 30).atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("Europe/Athens"))
        '2021-11-26T20:19:00Z'                     | Instant        | Instant.from(OffsetDateTime.of(LocalDateTime.of(2021, 11, 26, 21, 19), ZoneOffset.ofHours(1)))
        'PT0.01S'                                  | Duration       | Duration.ofMillis(10)
        'PT10S'                                    | Duration       | Duration.ofSeconds(10)
        'PT10M'                                    | Duration       | Duration.ofMinutes(10)
        'PT240H'                                   | Duration       | Duration.ofDays(10)
        'PT10H'                                    | Duration       | Duration.ofHours(10)
        'PT0.00000001S'                            | Duration       | Duration.ofNanos(10)

        typeName = type.simpleName
    }
}
