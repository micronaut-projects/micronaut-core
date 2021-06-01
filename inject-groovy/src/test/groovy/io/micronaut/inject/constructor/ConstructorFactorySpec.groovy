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
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Provided
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

/**
 * Created by graemerocher on 12/05/2017.
 */
class ConstructorFactorySpec extends Specification {

    void "test injection with constructor supplied by a provider"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a constructor that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
        b.a.c != null
        b.a.c2 != null
        b.a.d != null
        b.a.is(context.getBean(AImpl))
    }

    static interface A {

    }

    static interface C {

    }

    @Singleton
    static class D {}

    @Singleton
    static class CImpl implements C {

    }

    static class AImpl implements A {
        final C c
        final C c2
        protected D d

        AImpl(C c, C c2) {
            this.c = c
            this.c2 = c2
        }
    }

    @Factory
    static class AProvider implements Provider<A> {
        final C c
        @Inject C another
        @Inject protected D d

        @Inject AProvider(C c) {
            this.c = c
        }

        @Override
        @Singleton
        A get() {
            def impl = new AImpl(c, another)
            impl.d = d
            return impl
        }
    }

    static class B {
        private A a

        @Inject
        B(A a) {
            this.a = a
        }

        A getA() {
            return this.a
        }
    }

}
