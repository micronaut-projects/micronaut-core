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
package io.micronaut.inject.factory

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ParametrizedFactorySpec extends Specification  {
    void "test parametrized factory definition"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        C c = beanContext.createBean(C, Collections.singletonMap("count", 10))

        expect:
        c != null
        c.count == 10
        c.b != null

    }

    void "test parametrized factory definition missing parameter"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when:
        C c = beanContext.createBean(C)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('Missing bean argument [int count] for type: io.micronaut.inject.factory.ParametrizedFactorySpec$C. Required arguments: int count')

    }

    void "test parametrized factory definition invalid parameter"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()

        when:
        C c = beanContext.createBean(C, Collections.singletonMap("count", "test"))

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('Invalid bean argument [int count]. Cannot convert object [test] to required type: int')

    }

    static class B {
        String name
    }

    @Singleton
    static class A {
        String name = "A"
    }

    static class C {
        B b
        int count

        C(B b, int count) {
            this.b = b
            this.count = count
        }
    }

    @Factory
    static class BFactory {
        String name = "fromFactory"
        boolean postConstructCalled = false
        boolean getCalled = false
        @Inject private A fieldA
        @Inject protected A anotherField
        @Inject A a
        private A methodInjected
        @Inject private injectMe(A a) {
            methodInjected = a
        }
        A getFieldA() {
            return fieldA
        }

        A getAnotherField() {
            return anotherField
        }

        A getMethodInjected() {
            return methodInjected
        }

        @PostConstruct
        void init() {
            assertState()
            postConstructCalled = true
            name = name.toUpperCase()
        }

        @Singleton
        B get() {
            assert postConstructCalled : "post construct should have been called"
            assertState()

            getCalled = true
            return new B(name: name )
        }

        @Prototype
        C buildC(B b, @Parameter int count) {
            return new C(b, count)
        }

        private void assertState() {
            assert fieldA: "private fields should have been injected first"
            assert anotherField: "protected fields should have been injected field"
            assert a: "public properties should have been injected first"
            assert methodInjected: "methods should have been injected first"
        }
    }
}
