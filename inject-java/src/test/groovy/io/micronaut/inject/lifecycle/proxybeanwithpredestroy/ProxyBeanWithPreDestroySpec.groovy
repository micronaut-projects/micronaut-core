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
package io.micronaut.inject.lifecycle.proxybeanwithpredestroy

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import spock.lang.Specification
// proxyTarget = false proxies are always destroyed
class ProxyBeanWithPreDestroySpec extends Specification {

    def setup() {
        B.interceptCalled = 0
        B.injectedDestroyCalled = 0
        B.noArgsDestroyCalled = 0
        C.closed = 0
        D.destroyed = 0
        D.created = 0
    }

    def cleanup() {
        B.interceptCalled = 0
        B.injectedDestroyCalled = 0
        B.noArgsDestroyCalled = 0
        C.closed = 0
        D.destroyed = 0
        D.created = 0
    }

    void "test cannot destroyed a proxy bean by the class name"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            context.getBean(B)
            context.destroyBean(B)
        then:
            thrown(IllegalArgumentException)
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.destroyed == 0

        cleanup:
            context.close()
    }

    void "test that a pre-destroy hook works"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            Root root = context.getBean(Root)

        then:
            root.b.a != null
            B.interceptCalled == 1
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.destroyed == 0

        when:
            context.destroyBean(root)

        then:
            B.interceptCalled == 2
            B.noArgsDestroyCalled == 1
            B.injectedDestroyCalled == 1
            D.created == 1
            D.destroyed == 1

        cleanup:
            context.close()
    }

    void "test that a pre-destroy hook works when destroyed by registration"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            def beanDefinition = context.findBeanDefinition(B).get()
            def registration = context.getBeanRegistration(beanDefinition)
            B b = registration.bean

        then:
            B.interceptCalled == 1
            b.a != null
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 1
            D.destroyed == 0

        when:
            context.destroyBean(registration)

        then:
            B.interceptCalled == 2
            B.noArgsDestroyCalled == 1
            B.injectedDestroyCalled == 1
            D.created == 1
            D.destroyed == 1

        cleanup:
            context.close()
    }

    void "test that a bean with a pre-destroy hook works closed on close"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            Root root = context.getBean(Root)
        then:
            root.b.a != null
            B.interceptCalled == 1
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 1
            D.destroyed == 0

        when:
            context.close()

        then:
            B.interceptCalled == 2
            B.noArgsDestroyCalled == 1
            B.injectedDestroyCalled == 1
            D.created == 1
            D.destroyed == 1
    }

    void "test that destroy events run in the right phase"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()


        when:
            def pre = context.getBean(CPreDestroyEventListener)
            def post = context.getBean(CDestroyedListener)
            def c = context.getBean(C)

        then:
            C.closed == 0

        when:
            context.close()

        then:
            pre.called == 1
            post.called == 1
            C.closed == 1
    }
}
