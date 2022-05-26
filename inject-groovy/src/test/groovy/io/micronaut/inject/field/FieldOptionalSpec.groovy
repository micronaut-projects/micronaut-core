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
package io.micronaut.inject.field

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Created by graemerocher on 30/05/2017.
 */
class FieldOptionalSpec extends Specification {


    void "test injection of optional objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an optional field with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        !b.c.isPresent()
    }

    static interface A {

    }

    static interface C {}

    @Singleton
    static class AImpl implements A {

    }

    static class B {
        @Inject
        private Optional<A> a

        @Inject
        private Optional<C> c

        A getA() {
            return this.a.get()
        }

        Optional<C> getC() {
            return c
        }
    }

}


