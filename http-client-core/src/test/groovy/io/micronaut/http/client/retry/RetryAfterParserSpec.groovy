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
package io.micronaut.http.client.retry

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RetryAfterParserSpec extends Specification {

    static final Clock SYSTEM = Clock.systemUTC()

    @Unroll
    void "absent / empty value parses to null (#desc)"() {
        expect:
        RetryAfterParser.parse(value, SYSTEM) == null

        where:
        desc        | value
        'null'      | null
        'empty'     | ''
        'whitespace'| '   '
    }

    @Unroll
    void "delta-seconds form: #raw → #expected"() {
        expect:
        RetryAfterParser.parse(raw, SYSTEM) == expected

        where:
        raw                       | expected
        '0'                       | Duration.ZERO
        '1'                       | Duration.ofSeconds(1)
        '120'                     | Duration.ofSeconds(120)
        '   42   '                | Duration.ofSeconds(42)   // surrounding whitespace tolerated
        '99999999999999999999'    | null                      // overflow → unparseable
    }

    void "HTTP-date form: future date with fixed clock yields the delta"() {
        given:
        def now = Instant.parse('2026-04-28T12:00:00Z')
        def clock = Clock.fixed(now, ZoneOffset.UTC)

        expect:
        RetryAfterParser.parse('Tue, 28 Apr 2026 12:00:30 GMT', clock) == Duration.ofSeconds(30)
    }

    void "HTTP-date form: past date coerces to ZERO"() {
        given:
        def now = Instant.parse('2026-04-28T12:00:00Z')
        def clock = Clock.fixed(now, ZoneOffset.UTC)

        expect:
        RetryAfterParser.parse('Tue, 28 Apr 2026 11:00:00 GMT', clock) == Duration.ZERO
    }

    void "malformed HTTP-date returns null"() {
        expect:
        RetryAfterParser.parse('not-a-date', SYSTEM) == null
        RetryAfterParser.parse('Mon, 32 Apr 2026 12:00:00 GMT', SYSTEM) == null
    }
}
