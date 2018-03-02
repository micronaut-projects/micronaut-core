/*
 * Copyright 2018 original authors
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
package org.particleframework.retry

import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author graemerocher
 * @since 1.0
 */
class RetryContextSpec extends Specification {

    void "test retry context next delay is exponential"() {

        given:
        RetryContext retryContext = new RetryContext(3, 2, Duration.of(1, ChronoUnit.SECONDS))


        when:
        retryContext.incrementAttempts()

        then:
        retryContext.canRetry()
        retryContext.nextDelay() == 2000


        when:
        retryContext.incrementAttempts()

        then:
        retryContext.canRetry()
        retryContext.nextDelay() == 4000

        when:
        retryContext.incrementAttempts()

        then:
        !retryContext.canRetry()
        retryContext.nextDelay() == 6000
    }

    void "test retry context next delay is exponential with max delay"() {

        given:
        RetryContext retryContext = new RetryContext(3, 2, Duration.of(1, ChronoUnit.SECONDS), Duration.of(3, ChronoUnit.SECONDS))


        when:
        retryContext.incrementAttempts()

        then:
        retryContext.canRetry()
        retryContext.nextDelay() == 2000


        when:
        retryContext.incrementAttempts()

        then:
        retryContext.canRetry()
        retryContext.nextDelay() == 4000

        when:
        retryContext.incrementAttempts()

        then:
        !retryContext.canRetry()
        retryContext.nextDelay() == 6000

    }
}
