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
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class BeanWithPreDestroySpec extends Specification{

    void "test that a bean with a pre-destroy hook works"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        !b.noArgsDestroyCalled
        !b.injectedDestroyCalled

        when:
        context.destroyBean(B)

        then:
        b.noArgsDestroyCalled
        b.injectedDestroyCalled
    }

    void "test that a bean with a pre-destroy hook works closed on close"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        !b.noArgsDestroyCalled
        !b.injectedDestroyCalled

        when:
        context.close()

        then:
        b.noArgsDestroyCalled
        b.injectedDestroyCalled
    }


    @Singleton
    static class C {

    }
    @Singleton
    static class A {

    }

    @Singleton
    static class B implements Closeable{

        boolean noArgsDestroyCalled = false
        boolean injectedDestroyCalled = false

        @Inject protected A another
        private A a

        @Inject
        void setA(A a ) {
            this.a = a
        }

        A getA() {
            return a
        }

        @Override
        @PreDestroy
        void close() {
            noArgsDestroyCalled = true
        }

        @PreDestroy
        void another(C c) {
            if(c != null) {
                injectedDestroyCalled = true
            }
        }
    }
}
