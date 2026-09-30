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
package io.micronaut.retry.intercept

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.CircuitBreaker
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CircuitBreakerPerBeanSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'CircuitBreakerPerBeanSpec'])

    void "beans inheriting the intercepted method have their own circuit"() {
        given:
        FirstService first = context.getBean(FirstService)
        SecondService second = context.getBean(SecondService)
        first.failing = true

        when:"the first bean opens its circuit"
        first.call()

        then:
        IllegalStateException e = thrown()
        e.message == 'first failed'
        first.invocations == 2

        when:"the second bean is called"
        String result = second.call()

        then:"its circuit is still closed"
        result == 'second'
        second.invocations == 1

        when:"the second bean fails"
        second.failing = true
        second.call()

        then:"it retries with its own configuration"
        e = thrown(IllegalStateException)
        e.message == 'second failed'
        second.invocations == 5
    }

    void "instances of a bean share its circuit"() {
        given:
        PrototypeService first = context.getBean(PrototypeService)
        PrototypeService second = context.getBean(PrototypeService)
        first.failing = true

        expect:
        !first.is(second)

        when:"the first instance opens the circuit"
        first.call()

        then:
        IllegalStateException e = thrown()
        e.message == 'prototype failed'
        first.invocations == 2

        when:"the second instance is called"
        second.call()

        then:"the circuit is open and the method is not invoked"
        e = thrown(IllegalStateException)
        e.message == 'prototype failed'
        second.invocations == 0
    }

    static abstract class BaseService {
        boolean failing
        int invocations

        abstract String name()

        String call() {
            invocations++
            if (failing) {
                throw new IllegalStateException(name() + ' failed')
            }
            return name()
        }
    }

    @Requires(property = 'spec.name', value = 'CircuitBreakerPerBeanSpec')
    @Singleton
    @CircuitBreaker(attempts = '1', delay = '1ms', reset = '1s')
    static class FirstService extends BaseService {
        @Override
        String name() {
            'first'
        }
    }

    @Requires(property = 'spec.name', value = 'CircuitBreakerPerBeanSpec')
    @Singleton
    @CircuitBreaker(attempts = '3', delay = '1ms', reset = '30s')
    static class SecondService extends BaseService {
        @Override
        String name() {
            'second'
        }
    }

    @Requires(property = 'spec.name', value = 'CircuitBreakerPerBeanSpec')
    @Prototype
    @CircuitBreaker(attempts = '1', delay = '1ms', reset = '30s')
    static class PrototypeService extends BaseService {
        @Override
        String name() {
            'prototype'
        }
    }
}
