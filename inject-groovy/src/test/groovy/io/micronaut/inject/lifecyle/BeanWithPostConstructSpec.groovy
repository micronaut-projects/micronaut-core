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
package io.micronaut.inject.lifecyle

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class BeanWithPostConstructSpec extends Specification{

    void "test that a bean with a protected post construct hook that the hook is invoked"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        b.injectedFirst
        b.setupComplete
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
            if(a != null && another != null) {
                injectedFirst = true
            }
            setupComplete = true
        }
    }
}
