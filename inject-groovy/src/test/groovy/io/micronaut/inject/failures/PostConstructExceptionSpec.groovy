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
package io.micronaut.inject.failures

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

import jakarta.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class PostConstructExceptionSpec extends Specification {

    void "test error message when a bean has an error in the post construct method"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(BeanInstantiationException)
        e.message == '''Error instantiating bean of type  [io.micronaut.inject.failures.PostConstructExceptionSpec$B]

Message: bad
Path Taken: new B()'''
    }


    @Singleton
    static class A {

    }
    @Singleton
    static class B {

        boolean setupComplete = false
        boolean injectedFirst = false

        @Inject protected A another
        private A a

        @Inject
        void setA(A a ) {
            this.a = a
        }

        A getA() {
            return a
        }

        @PostConstruct
        void setup() {
            throw new RuntimeException("bad")
        }
    }
}
