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
package io.micronaut.http.client

import io.micronaut.http.client.HttpClientConfiguration.RetryConfiguration
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class HttpClientConfigurationSpec extends Specification {

    void "RetryConfiguration defaults match the published contract"() {
        given:
        def cfg = new RetryConfiguration()

        expect: 'opt-in: disabled by default — zero behavior change for existing users'
        !cfg.enabled

        and: 'numeric defaults match the public RFC 9110 retry policy'
        cfg.attempts == 3
        cfg.delay == Duration.ofMillis(500)
        cfg.multiplier == 1.5d
        cfg.maxDelay == Duration.ofSeconds(10)
        cfg.jitter == 0.25d
        cfg.respectRetryAfter
    }

    void "RetryConfiguration#isEnabled returns false by default (Toggleable contract)"() {
        expect:
        !new RetryConfiguration().enabled
    }

    @Unroll
    void "RetryConfiguration setAttempts coerces #input to #expected (must be at least 1)"() {
        given:
        def cfg = new RetryConfiguration()

        when:
        cfg.attempts = input

        then:
        cfg.attempts == expected

        where:
        input         | expected
        Integer.MIN_VALUE | 1
        -5            | 1
        0             | 1
        1             | 1
        2             | 2
        7             | 7
        Integer.MAX_VALUE | Integer.MAX_VALUE
    }

    void "RetryConfiguration#setJitter rejects values outside [0, 1]"() {
        given:
        def cfg = new RetryConfiguration()

        when:
        cfg.jitter = -0.1

        then:
        thrown(IllegalArgumentException)

        when:
        cfg.jitter = 1.5

        then:
        thrown(IllegalArgumentException)

        when: 'boundary values accepted'
        cfg.jitter = 0.0
        cfg.jitter = 1.0

        then:
        cfg.jitter == 1.0
    }
}
