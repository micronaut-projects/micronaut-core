/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry

import io.micronaut.retry.annotation.RetryPredicate
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class ProgrammaticRetryPolicyValidationSpec extends Specification {

    @Unroll
    void "retry policy rejects invalid attempts #attempts"() {
        when:
        RetryPolicy.builder().maxAttempts(attempts).build()

        then:
        thrown(IllegalArgumentException)

        where:
        attempts << [0, -1]
    }

    @Unroll
    void "retry policy rejects negative durations"(Duration delay, Duration maxDelay) {
        when:
        RetryPolicy.builder()
            .delay(delay)
            .maxDelay(maxDelay)
            .build()

        then:
        thrown(IllegalArgumentException)

        where:
        delay                  | maxDelay
        Duration.ofMillis(-1)  | null
        Duration.ZERO          | Duration.ofMillis(-1)
    }

    @Unroll
    void "retry policy rejects invalid multiplier #multiplier"() {
        when:
        RetryPolicy.builder().multiplier(multiplier).build()

        then:
        thrown(IllegalArgumentException)

        where:
        multiplier << [-1d, -0.01d]
    }

    @Unroll
    void "retry policy rejects invalid jitter #jitter"() {
        when:
        RetryPolicy.builder().jitter(jitter).build()

        then:
        thrown(IllegalArgumentException)

        where:
        jitter << [-0.1d, 1.01d]
    }

    void "retry policy rejects null predicate"() {
        when:
        RetryPolicy.builder().predicate(null as RetryPredicate).build()

        then:
        thrown(NullPointerException)
    }

    void "retry policy rejects null captured exception"() {
        when:
        RetryPolicy.builder().capturedException(null).build()

        then:
        thrown(NullPointerException)
    }

    @Unroll
    void "circuit breaker policy rejects invalid reset timeout #resetTimeout"() {
        when:
        CircuitBreakerPolicy.builder().resetTimeout(resetTimeout).build()

        then:
        thrown(IllegalArgumentException)

        where:
        resetTimeout << [Duration.ZERO, Duration.ofMillis(-1)]
    }
}
