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
package io.micronaut.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.CircuitBreaker
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class NamedCircuitBreakerResolutionSpec extends Specification {

    void "beans sharing an inherited method keep the named circuit of their class, whichever is called first"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'NamedCircuitBreakerResolutionSpec'])
        CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry)
        FirstCircuitService first = context.getBean(FirstCircuitService)
        SecondCircuitService second = context.getBean(SecondCircuitService)
        BaseCircuitService opened = firstOpens ? first : second
        BaseCircuitService other = firstOpens ? second : first
        String openedCircuit = firstOpens ? 'first' : 'second'
        String otherCircuit = firstOpens ? 'second' : 'first'

        when: "one bean fails with its one retry"
        opened.call(true)

        then: "the circuit of its class opens"
        thrown(IllegalStateException)
        opened.calls.get() == 2
        registry.findState(openedCircuit).get() == CircuitState.OPEN
        registry.findState(otherCircuit).isEmpty()

        when: "the other bean is called"
        String result = other.call(false)

        then: "it calls through the closed circuit of its own class"
        result == "ok"
        other.calls.get() == 1
        registry.findState(otherCircuit).get() == CircuitState.CLOSED
        registry.findState(openedCircuit).get() == CircuitState.OPEN

        when: "the bean of the open circuit is called"
        opened.call(false)

        then: "it fails fast"
        thrown(IllegalStateException)
        opened.calls.get() == 2

        cleanup:
        context.close()

        where:
        firstOpens << [true, false]
    }

    static abstract class BaseCircuitService {
        final AtomicInteger calls = new AtomicInteger()

        String call(boolean fail) {
            calls.incrementAndGet()
            if (fail) {
                throw new IllegalStateException("down")
            }
            return "ok"
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedCircuitBreakerResolutionSpec')
    @CircuitBreaker(name = 'first', attempts = '1', delay = '1ms', reset = '1m')
    static class FirstCircuitService extends BaseCircuitService {
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NamedCircuitBreakerResolutionSpec')
    @CircuitBreaker(name = 'second', attempts = '1', delay = '1ms', reset = '1m')
    static class SecondCircuitService extends BaseCircuitService {
    }
}
