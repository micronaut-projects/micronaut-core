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
import spock.lang.Specification

/**
 * The fail-on and skip-on of a named circuit breaker are class names, resolved when its policy
 * is built.
 */
class NamedCircuitBreakerConfigurationSpec extends Specification {

    void "fail-on and skip-on bind a list of class names"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.retry.circuit-breakers.orders.fail-on': ['java.io.IOException', 'java.lang.IllegalStateException'],
            'micronaut.retry.circuit-breakers.orders.skip-on': ['java.io.FileNotFoundException'],
        ])

        when:
        CircuitBreakerWindow window = window(context, "orders")

        then:
        window.failOn() == [IOException, IllegalStateException]
        window.skipOn() == [FileNotFoundException]

        cleanup:
        context.close()
    }

    void "fail-on and skip-on bind comma-separated class names"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.retry.circuit-breakers.orders.fail-on': 'java.io.IOException, java.lang.IllegalStateException',
            'micronaut.retry.circuit-breakers.orders.skip-on': 'java.io.FileNotFoundException,java.util.NoSuchElementException',
        ])

        when:
        CircuitBreakerWindow window = window(context, "orders")

        then:
        window.failOn() == [IOException, IllegalStateException]
        window.skipOn() == [FileNotFoundException, NoSuchElementException]

        and: "the circuit of the name counts by them"
        CircuitBreakerGuard guard = context.getBean(CircuitBreakerRegistry).guard("orders")
        guard.acquire().onFailure(new NoSuchElementException("skipped"))
        context.getBean(CircuitBreakerRegistry).findSnapshot("orders").get().failures() == 0

        cleanup:
        context.close()
    }

    void "a #property class name that is not found fails the startup with the circuit breaker and the property"() {
        when:
        ApplicationContext.run([
            ("micronaut.retry.circuit-breakers.typo." + property): ['java.io.IOExeption'],
        ])

        then:
        Exception e = thrown()
        String message = messages(e)
        message.contains('Invalid circuit breaker [typo] of micronaut.retry.circuit-breakers.typo')
        message.contains("$property must be exception types, class not found: java.io.IOExeption")

        where:
        property << ['fail-on', 'skip-on']
    }

    void "a #property class that is not an exception fails the window with the circuit breaker and the property"() {
        given:
        NamedCircuitBreakerConfiguration configuration = new NamedCircuitBreakerConfiguration("bad")
        configuration."$setter"(['java.lang.String'])

        when:
        configuration.toWindow()

        then:
        IllegalArgumentException e = thrown()
        e.message == "Invalid circuit breaker [bad] of micronaut.retry.circuit-breakers.bad: $property must be exception types, got java.lang.String"

        where:
        property  | setter
        'fail-on' | 'setFailOn'
        'skip-on' | 'setSkipOn'
    }

    private static CircuitBreakerWindow window(ApplicationContext context, String name) {
        return context.getBeansOfType(NamedCircuitBreakerConfiguration).find { it.name == name }.toWindow()
    }

    private static String messages(Throwable e) {
        List<String> messages = []
        while (e != null) {
            messages << e.message
            e = e.cause == e ? null : e.cause
        }
        return messages.join('\n')
    }
}
