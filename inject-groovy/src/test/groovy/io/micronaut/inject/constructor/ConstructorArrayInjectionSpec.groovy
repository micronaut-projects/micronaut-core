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
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConstructorArrayInjectionSpec extends Specification {


    void "test injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
        b.all.contains(context.getBean(AnotherImpl))
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    @Singleton
    static class AnotherImpl implements A {

    }

    static class B {
        private A[] all

        @Inject
        B(A[] all) {
            this.all = all
        }

        List<A> getAll() {
            return all.toList()
        }
    }

}



