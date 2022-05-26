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
package io.micronaut.inject.method

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.annotation.Nullable
import jakarta.inject.Inject

/**
 * Created by graemerocher on 30/05/2017.
 */
class SetterWithNullableSpec extends Specification {


    void "test injection of nullable objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an setter with @Inject and @Nullable"
        B b =  context.getBean(B)

        then:"The implementation is not injected, but null is"
        b.a == null
    }

    void "test normal injection still fails"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an setter with @Inject"
        C c =  context.getBean(C)

        then:"The bean is not found"
        thrown(DependencyInjectionException)
    }

    static interface A {

    }

    static class B {
        A a

        @Inject
        void setA(@Nullable A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

    static class C {
        A a

        @Inject
        void setA(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }

    }

}



