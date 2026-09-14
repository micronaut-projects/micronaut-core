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
package io.micronaut.inject.lifecycle.proxytargetbeanprototypewithpredestroy

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import spock.lang.Specification

class ProxyLazyCachedTargetPrototypeBeanWithPreDestroySpec extends Specification {

    def setup() {
        B.interceptCalled = 0
        B.injectedDestroyCalled = 0
        B.noArgsDestroyCalled = 0
        C.closed = 0
        D.destroyed = 0
        D.created = 0
        D.destroyedInstances.clear()
    }

    def cleanup() {
        B.interceptCalled = 0
        B.injectedDestroyCalled = 0
        B.noArgsDestroyCalled = 0
        C.closed = 0
        D.destroyed = 0
        D.created = 0
        D.destroyedInstances.clear()
    }

    void "test that a lazy target bean with a pre-destroy hook works"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            Root root = context.getBean(Root)
            root.triggerProxyInitializeForB()

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
            D.created == 2 // Create proxy + target
            // The proxy owns one D and the lazy cached target owns another, and only the proxy's is destroyed:
            // the target instance is cached on the proxy without its registration, so the beans created for the
            // target are out of reach when the proxy is destroyed. Until 5.2.1 this read as two destructions,
            // because the proxy's dependents were destroyed once in the proxy and then handed to the target and
            // destroyed again - the same D twice, never the target's.
            D.destroyed == 1
            D.destroyedInstances.unique(false).size() == 1

        cleanup:
            context.close()
    }

    void "test that a proxy pre-destroy is not called on not-initialized target"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            Root root = context.getBean(Root)

        then:
            B.interceptCalled == 0
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 1 // Create proxy
            D.destroyed == 0

        when:
            context.destroyBean(root)

        then: "Lazy target or proxy is not destroyed"
            B.interceptCalled == 0
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 1
            D.destroyed == 1

        cleanup:
            context.close()
    }

    void "test that a lazy proxy bean with a pre-destroy hook works when destroyed by registration"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            def beanDefinition = context.findBeanDefinition(B).get()
            def registration = context.getBeanRegistration(beanDefinition)
            B b = registration.bean
            b.getA() // B is lazy

        then:
            b.a != null
            B.interceptCalled == 1
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 2  // Create proxy + target
            D.destroyed == 0

        when:
            context.destroyBean(registration)

        then:
            B.interceptCalled == 2
            B.noArgsDestroyCalled == 1
            B.injectedDestroyCalled == 1
            D.created == 2
            // One D destroyed, and only one: see the first feature for why it is not two.
            D.destroyed == 1
            D.destroyedInstances.unique(false).size() == 1

        cleanup:
            context.close()
    }

    void "test that a lazy proxy bean with a pre-destroy hook is not called on not-initialized target"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            def beanDefinition = context.findBeanDefinition(B).get()
            def registration = context.getBeanRegistration(beanDefinition)
            registration.bean

        then:
            B.interceptCalled == 0
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 1 // Create proxy
            D.destroyed == 0

        when:
            context.destroyBean(registration)

        then:
            B.interceptCalled == 0
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 1
            D.destroyed == 1 // Destroy proxy

        cleanup:
            context.close()
    }

    void "test that a bean with a pre-destroy hook works closed on close"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            Root root = context.getBean(Root)
            root.triggerProxyInitializeForB()

        then:
            root.b.a != null
            B.interceptCalled == 1
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 2
            D.destroyed == 0

        when:
            context.close()

        then:
            B.interceptCalled == 2
            B.noArgsDestroyCalled == 1
            B.injectedDestroyCalled == 1
            D.created == 2
            // One D destroyed, and only one: see the first feature for why it is not two.
            D.destroyed == 1
            D.destroyedInstances.unique(false).size() == 1
    }

    void "test proxies are prototypes and dependent beans not destroyed when created by `getBean(<ProxyClass>)`"() {
        given:
            BeanContext context = ApplicationContext.builder().properties("spec": getClass().getSimpleName()).build()
            context.start()

        when:
            B b = context.getBean(B)
            b.getA() // B is lazy

        then:
            b.a != null
            B.interceptCalled == 1
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 2
            D.destroyed == 0

        when:
            context.close()

        then:
            // Prototype target is not destroyed
            B.interceptCalled == 1
            B.noArgsDestroyCalled == 0
            B.injectedDestroyCalled == 0
            D.created == 2
            D.destroyed == 0
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
