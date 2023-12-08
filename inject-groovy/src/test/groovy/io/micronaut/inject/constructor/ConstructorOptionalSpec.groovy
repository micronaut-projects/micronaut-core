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

import io.micronaut.context.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
/**
 * Created by graemerocher on 30/05/2017.
 */
class ConstructorOptionalSpec extends Specification {


    void "test injection of optional objects"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has an optional constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        !b.c.isPresent()

        cleanup:
        context.close()
    }

    static interface A {

    }

    static interface C {}

    @Singleton
    static class AImpl implements A {

    }

    static class B {
        private Optional<A> a
        private Optional<C> c

        @Inject
        B(Optional<A> a, Optional<C> c) {
            this.a = a
            this.c = c
        }

        A getA() {
            return this.a.get()
        }

        Optional<C> getC() {
            return c
        }
    }

}


