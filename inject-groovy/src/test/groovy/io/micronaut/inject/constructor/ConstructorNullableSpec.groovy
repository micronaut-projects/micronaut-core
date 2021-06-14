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
package io.micronaut.inject.constructor

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.annotation.Nullable
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Provider

class ConstructorNullableSpec extends Specification {


    void "test nullable injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is not injected, but null is"
        b.a == null

        cleanup:
        context.close()
    }

    void "test nullable provider injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        D d =  context.getBean(D)

        then:"The implementation is not injected, but null is"
        noExceptionThrown()
        d.@a == null

        cleanup:
        context.close()
    }

    void "test normal injection still fails"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        C c =  context.getBean(C)

        then:"The bean is not found"
        thrown(DependencyInjectionException)

        cleanup:
        context.close()
    }

    static interface A {

    }

    static class B {
        private A a

        @Inject
        B(@Nullable A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

    static class C {
        private A a

        @Inject
        B(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

    static class D {

        private final Provider<A> a

        @Inject
        D(@Nullable Provider<A> a) {
            this.a = a
        }

        A getA() {
            return this.a != null ? this.a.get() : null;
        }
    }
}
