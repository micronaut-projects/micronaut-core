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
                new DefaultRetryPredicate(Collections.singletonList(DiscoveryException.class), Collections.emptyList())
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
                new DefaultRetryPredicate(Collections.emptyList(), Collections.singletonList(DiscoveryException.class))
        )
        RuntimeException r = new RuntimeException("bad")

        expect:
        retryContext.canRetry(r)
        !retryContext.canRetry(new DiscoveryException("something"))
        !retryContext.canRetry(new RegistrationException("something"))
    }

    void "test retry context next delay is exponential"() {

        given:
        SimpleRetry retryContext = new SimpleRetry(3, 2, Duration.of(1, ChronoUnit.SECONDS))
        RuntimeException r = new RuntimeException("bad")

        when:
        retryContext.canRetry(r)

        then:
        retryContext.currentAttempt() == 1
        retryContext.nextDelay() == 4000


        when:
        retryContext.canRetry(r)

        then:

        retryContext.currentAttempt() == 2
        retryContext.nextDelay() == 6000

        when:
        !retryContext.canRetry(r)

        then:

        retryContext.currentAttempt() == 3
        retryContext.nextDelay() == 8000
    }

    void "test retry context next delay is exponential with max delay"() {

        given:
        SimpleRetry retryContext = new SimpleRetry(3, 1, Duration.of(1, ChronoUnit.SECONDS), Duration.of(3, ChronoUnit.SECONDS))
        RuntimeException r = new RuntimeException("bad")

        when:
        boolean canRetry = retryContext.canRetry(r)

        then:
        canRetry
        retryContext.nextDelay() == 2000


        when:
        retryContext.canRetry(r)

        then:
        canRetry
        retryContext.nextDelay() == 3000

        when:
        canRetry = retryContext.canRetry(r)

        then:
        !canRetry
        retryContext.nextDelay() == 4000

    }
}
