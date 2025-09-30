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

import io.micronaut.core.convert.DefaultMutableConversionService
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount

class TimeConverterRegistrarSpec extends Specification {

    @Unroll
    void "test convert duration #val"() {
        given:
        def conversionService = new DefaultMutableConversionService()
        new TimeConverterRegistrar().register(conversionService)

        expect:
        conversionService.convert(val, Duration).get() == expected

        where:
        val    | expected
        '10ns' | Duration.ofNanos(10)
        '10us' | Duration.ofNanos(10_000)
        '10ms' | Duration.ofMillis(10)
        '10s'  | Duration.ofSeconds(10)
        '10m'  | Duration.ofMinutes(10)
        '10h'  | Duration.ofHours(10)
        '10d'  | Duration.ofDays(10)
    }

    @Unroll
    void "test convert period #val"() {
        given:
        def conversionService = new DefaultMutableConversionService()
        new TimeConverterRegistrar().register(conversionService)

        expect:
        conversionService.convert(val, Period).get() == expected

        where:
        val   | expected
        '10d' | Period.ofDays(10)
        '10D' | Period.ofDays(10)
        '10w' | Period.ofWeeks(10)
        '10m' | Period.ofMonths(10)
        '10M' | Period.ofMonths(10)
        '10y' | Period.ofYears(10)
    }

    @Unroll
    void "test convert to TemporalAmount #val"() {
        given:
        def conversionService = new DefaultMutableConversionService()
        new TimeConverterRegistrar().register(conversionService)

        expect:
        conversionService.convert(val, TemporalAmount).get() == expected

        where:
        val   | expected
        '10ns' | Duration.ofNanos(10)
        '10ms' | Duration.ofMillis(10)
        '10s' | Duration.ofSeconds(10)
        '10m' | Duration.ofMinutes(10)
        '10d' | Duration.ofDays(10)
        '10D' | Period.ofDays(10)
        '10M' | Period.ofMonths(10)
        '10w' | Period.ofWeeks(10)
        '10y' | Period.ofYears(10)
    }

    @Unroll
    void "test converts a #sourceObject.class.name to a #targetType.name"() {
        given:
        def conversionService = new DefaultMutableConversionService()
        new TimeConverterRegistrar().register(conversionService)

        expect:
        conversionService.convert(sourceObject, targetType).get() == result

        where:
        sourceObject                                                            | targetType     | result
        Instant.ofEpochMilli(123)                                               | Date           | new Date(123)
        Instant.ofEpochMilli(123).atOffset(ZoneOffset.ofHours(5))               | Date           | new Date(123)
        Instant.ofEpochMilli(123).atZone(ZoneId.of("Europe/Berlin"))            | Date           | new Date(123)
        Instant.ofEpochMilli(123).atOffset(ZoneOffset.UTC).toLocalDateTime()    | Date           | new Date(123)
        Instant.ofEpochMilli(123).atOffset(ZoneOffset.UTC).toLocalDate()        | Date           | new Date(0)

        "2022-08-12"                                                            | LocalDate      | LocalDate.of(2022, 8, 12)
        "2022-08-12T12:19:00"                                                   | LocalDateTime  | LocalDateTime.of(2022, 8, 12, 12, 19)
        "12:19:00+05:00"                                                        | OffsetTime     | OffsetTime.of(12, 19, 0, 0, ZoneOffset.ofHours(5))
        "2022-08-12T12:19:00+05:00"                                             | OffsetDateTime | OffsetDateTime.of(2022, 8, 12, 12, 19, 0, 0, ZoneOffset.ofHours(5))
        "2022-08-12T12:19:00+05:00"                                             | ZonedDateTime  | ZonedDateTime.of(2022, 8, 12, 12, 19, 0, 0, ZoneOffset.ofHours(5))
        "2022-08-12T12:19:00+02:00[Europe/Berlin]"                              | ZonedDateTime  | ZonedDateTime.of(2022, 8, 12, 12, 19, 0, 0, ZoneId.of("Europe/Berlin"))
        "2022-08-12T12:19:00Z"                                                  | Instant        | LocalDateTime.of(2022, 8, 12, 12, 19).toInstant(ZoneOffset.UTC)
        "12:19:00"                                                              | LocalTime      | LocalTime.of(12, 19)
        "--12-19"                                                               | MonthDay       | MonthDay.of(12, 19)
        "2022-08"                                                               | YearMonth      | YearMonth.of(2022, 8)
        "2022"                                                                  | Year           | Year.of(2022)
        2022                                                                    | Year           | Year.of(2022)
        "+07:00"                                                                | ZoneId         | ZoneId.of("+07:00")

        LocalDate.of(2022, 8, 12)                                               | String         | "2022-08-12"
        LocalDateTime.of(2022, 8, 12, 12, 19)                                   | String         | "2022-08-12T12:19:00"
        OffsetTime.of(12, 19, 0, 0, ZoneOffset.ofHours(5))                      | String         | "12:19:00+05:00"
        OffsetDateTime.of(2022, 8, 12, 12, 19, 0, 0, ZoneOffset.ofHours(5))     | String         | "2022-08-12T12:19:00+05:00"
        ZonedDateTime.of(2022, 8, 12, 12, 19, 0, 0, ZoneOffset.ofHours(5))      | String         | "2022-08-12T12:19:00+05:00"
        ZonedDateTime.of(2022, 8, 12, 12, 19, 0, 0, ZoneId.of("Europe/Berlin")) | String         | "2022-08-12T12:19:00+02:00[Europe/Berlin]"
        LocalDateTime.of(2022, 8, 12, 12, 19).toInstant(ZoneOffset.UTC)         | String         | "2022-08-12T12:19:00Z"
        LocalTime.of(12, 19)                                                    | String         | "12:19:00"
        MonthDay.of(12, 19)                                                     | String         | "--12-19"
        YearMonth.of(2022, 8)                                                   | String         | "2022-08"
        Year.of(2022)                                                           | String         | "2022"
        ZoneId.of("+07:00")                                                     | String         | "+07:00"
    }
}
