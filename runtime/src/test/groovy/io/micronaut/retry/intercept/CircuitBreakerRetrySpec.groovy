/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.retry.CircuitState
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration

/**
 * @author graemerocher
 * @since 1.0
 */
class CircuitBreakerRetrySpec extends Specification {


    void "test circuit breaker retry"() {
        when:"A retry is constructed"
        CircuitBreakerRetry retry = new CircuitBreakerRetry(
                1000,
                {->
                    new SimpleRetry(3, 2.0d, Duration.ofMillis(500))
                }, null,null
        )
        retry.open()


        then:
        retry.currentState() == CircuitState.CLOSED

        when:
        retry.close(null)

        then:
        retry.currentState() == CircuitState.CLOSED


        when:
        retry.open()


        then:
        retry.canRetry(new RuntimeException("bad"))
        retry.canRetry(new RuntimeException("bad"))
        retry.canRetry(new RuntimeException("bad"))
        !retry.canRetry(new RuntimeException("bad"))

        when:
        retry.close(new RuntimeException("bad"))

        then:
        retry.currentState() == CircuitState.OPEN


        when:
        PollingConditions conditions = new PollingConditions(timeout: 3)
        retry.open()

        then:
        def e = thrown(RuntimeException)
        e.message == "bad"
        conditions.eventually {
            retry.currentState() == CircuitState.HALF_OPEN
        }

        when:
        retry.open()
        retry.close(new RuntimeException("another bad"))

        then:
        retry.currentState() == CircuitState.OPEN


        when:
        retry.open()

        then:
        retry.currentState() == CircuitState.OPEN
        e = thrown(RuntimeException)
        e.message == "another bad"
        conditions.eventually {
            retry.currentState() == CircuitState.HALF_OPEN
        }

        when:
        retry.open()
        retry.close(null)

        then:
        retry.currentState() == CircuitState.CLOSED
        retry.canRetry(new RuntimeException("bad"))

    }
}
