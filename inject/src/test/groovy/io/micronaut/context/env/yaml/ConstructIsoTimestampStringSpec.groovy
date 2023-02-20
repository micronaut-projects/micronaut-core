package io.micronaut.context.env.yaml

import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.Temporal

class ConstructIsoTimestampStringSpec extends Specification {
    def parse(String literal, Temporal expected) {
        when:
        Temporal parsed = ConstructIsoTimestampString.parse(literal)
        then:
        parsed == expected

        where:
        literal                         | expected
        '2022-08-12'                    | LocalDate.of(2022, 8, 12)
        '2022-08-12T10:01:02.345'       | LocalDateTime.of(2022, 8, 12, 10, 1, 2, 345_000_000)
        '2022-08-12T10:01:02.345Z'      | LocalDateTime.of(2022, 8, 12, 10, 1, 2, 345_000_000).atOffset(ZoneOffset.UTC)
        '2022-08-12T10:01:02.345+05'    | LocalDateTime.of(2022, 8, 12, 10, 1, 2, 345_000_000).atOffset(ZoneOffset.ofHours(5))
        '2022-08-12T10:01:02.345+05:06' | LocalDateTime.of(2022, 8, 12, 10, 1, 2, 345_000_000).atOffset(ZoneOffset.ofHoursMinutes(5, 6))
    }
}
