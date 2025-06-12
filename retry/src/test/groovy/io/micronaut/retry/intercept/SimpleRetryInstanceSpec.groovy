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
package io.micronaut.retry.intercept

import io.micronaut.discovery.exceptions.DiscoveryException
import io.micronaut.discovery.registration.RegistrationException
import io.micronaut.retry.annotation.DefaultRetryPredicate
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author graemerocher
 * @since 1.0
 */
class SimpleRetryInstanceSpec extends Specification {

    void "test retry context includes"() {
        given:
        SimpleRetry simpleRetry = new SimpleRetry(
                3,
                2,
                Duration.of(1, ChronoUnit.SECONDS),
                null,
                new DefaultRetryPredicate(Collections.singletonList(DiscoveryException.class), Collections.emptyList()),
                RuntimeException.class,
                0
        )
        RuntimeException r = new RuntimeException("bad")

        expect:
        !simpleRetry.canRetry(r)
        simpleRetry.canRetry(new DiscoveryException("something"))
        simpleRetry.canRetry(new RegistrationException("something"))
    }

    void "test retry context excludes"() {
        given:
        SimpleRetry retryContext = new SimpleRetry(
                3,
                2,
                Duration.of(1, ChronoUnit.SECONDS),
                null,
                new DefaultRetryPredicate(Collections.emptyList(), Collections.singletonList(DiscoveryException.class)),
                RuntimeException.class,
                0
        )
        RuntimeException r = new RuntimeException("bad")

        expect:
        retryContext.canRetry(r)
        !retryContext.canRetry(new DiscoveryException("something"))
        !retryContext.canRetry(new RegistrationException("something"))
    }

    void "test retry context next delay is exponential"() {

        given:
        SimpleRetry retryContext = new SimpleRetry(3, 2, Duration.of(1, ChronoUnit.SECONDS), 0)
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        retryContext.currentAttempt() == 1
        retryContext.nextDelay() == 1000


        when:
        retryContext.canRetry(r)

        then:

        retryContext.currentAttempt() == 2
        retryContext.nextDelay() == 2000

        when:
        !retryContext.canRetry(r)

        then:

        retryContext.currentAttempt() == 3
        retryContext.nextDelay() == 4000
    }

    void "test retry context next delay is exponential with max delay"() {

        given:
        SimpleRetry retryContext = new SimpleRetry(
                3,
                2,
                Duration.of(1, ChronoUnit.SECONDS),
                Duration.of(3, ChronoUnit.SECONDS),
                RuntimeException.class,
                0
        )
        RuntimeException r = new RuntimeException("bad")

        when:
        boolean canRetry = retryContext.canRetry(r)

        then:
        canRetry
        retryContext.nextDelay() == 1000


        when:
        canRetry = retryContext.canRetry(r)

        then:
        canRetry
        retryContext.nextDelay() == 2000

        when:
        canRetry = retryContext.canRetry(r)

        then:
        !canRetry
        retryContext.nextDelay() == 4000

    }

    void "test retry context next delay with negative jitter"() {
        given:
        SimpleRetry retryContext = new SimpleRetry(
                1,
                1,
                Duration.of(1, ChronoUnit.SECONDS),
                -0.25
        )
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        retryContext.nextDelay() == 1000
    }

    void "test retry context next delay with zero jitter"() {
        given:
        SimpleRetry retryContext = new SimpleRetry(
                1,
                1,
                Duration.of(1, ChronoUnit.SECONDS),
                0
        )
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        retryContext.nextDelay() == 1000
    }

    void "test retry context next delay with positive jitter"() {
        given:
        SimpleRetry retryContext = new SimpleRetry(
                1,
                1,
                Duration.of(1, ChronoUnit.SECONDS),
                0.25
        )
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        long delay = retryContext.nextDelay()
        delay >= 750 && delay <= 1250
    }

    void "test retry context next delay with jitter greater than 1.0"() {
        given:
        SimpleRetry retryContext = new SimpleRetry(
                1,
                1,
                Duration.of(1, ChronoUnit.SECONDS),
                1.25
        )
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        long delay = retryContext.nextDelay()
        delay >= 0 && delay <= 2250
    }

    void "test retry context next delay is exponential with jitter"() {
        given:
        SimpleRetry retryContext = new SimpleRetry(
                3,
                2,
                Duration.of(1, ChronoUnit.SECONDS),
                0.25
        )
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        retryContext.currentAttempt() == 1
        long delay1 = retryContext.nextDelay()
        delay1 >= 750 && delay1 <= 1250

        when:
        retryContext.canRetry(r)

        then:
        retryContext.currentAttempt() == 2
        long delay2 = retryContext.nextDelay()
        delay2 >= 1500 && delay2 <= 2500

        when:
        retryContext.canRetry(r)

        then:
        retryContext.currentAttempt() == 3
        long delay3 = retryContext.nextDelay()
        delay3 >= 3000 && delay3 <= 5000
    }
}
