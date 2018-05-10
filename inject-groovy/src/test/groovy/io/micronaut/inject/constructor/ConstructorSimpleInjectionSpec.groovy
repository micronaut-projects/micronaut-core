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
package io.micronaut.inject.constructor

import groovy.transform.PackageScope
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConstructorSimpleInjectionSpec extends Specification {


    void "test injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)
        B2 b2 =  context.getBean(B2)

        then:"The implementation is injected"
        b.a != null
        b2.a != null
        b2.a2 != null
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    static class B {
        private A a

        @PackageScope
        @Inject
        B(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }
    }

    static class B2 {
        private A a
        private A a2

        @Inject
        B(A a, A a2) {
            this.a = a
            this.a2 = a2
        }

        A getA() {
            return this.a
        }

        A getA2() {
            return a2
        }
    }
}


