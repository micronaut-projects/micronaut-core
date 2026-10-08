/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.registration

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.scope.CreatedBean
import io.micronaut.inject.BeanDefinition

/**
 * A registration tells whether it holds an instance created for the lookup that returned it, which closing it
 * destroys without touching an instance anyone else holds.
 */
class BeanRegistrationOwnedByCallerSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

@Singleton
class SingletonBean {
    static int destroyed;

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}

@Prototype
class PrototypeBean {
    static int destroyed;

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}

@Bean
class UnscopedBean {
}

@Refreshable
class RefreshableBean {
    static int destroyed;

    public RefreshableBean target() {
        return this;
    }

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}

@io.micronaut.runtime.context.scope.ThreadLocal
class ThreadLocalBean {
}
'''

    void "a registration is owned by the caller only when the lookup created its instance for the caller"() {
        given:
        ApplicationContext context = buildContext('test.SingletonBean', SOURCE, true)

        expect:
        !registration(context, 'test.SingletonBean').isOwnedByCaller()
        registration(context, 'test.PrototypeBean').isOwnedByCaller()
        registration(context, 'test.UnscopedBean').isOwnedByCaller()
        !registration(context, 'test.RefreshableBean').isOwnedByCaller()
        !registration(context, 'test.ThreadLocalBean').isOwnedByCaller()

        and: 'a registration built by hand is not'
        BeanRegistration<?> registration = registration(context, 'test.SingletonBean')
        !new BeanRegistration(registration.identifier, registration.beanDefinition, registration.bean).isOwnedByCaller()

        cleanup:
        context.close()
    }

    void "an instance created for the caller is owned by the caller, whatever the scope of its definition"() {
        given:
        ApplicationContext context = buildContext('test.SingletonBean', SOURCE, true)
        BeanDefinition<?> definition = context.getBeanDefinition(context.classLoader.loadClass('test.SingletonBean'))

        when:
        CreatedBean<?> created = context.createBeanRegistration(definition)

        then:
        created instanceof BeanRegistration
        ((BeanRegistration<?>) created).isOwnedByCaller()
        !created.bean().is(context.getBean(definition.beanType))

        cleanup:
        created?.close()
        context.close()
    }

    void "closing only the registrations owned by the caller a lookup returned leaves the shared beans alive"() {
        given:
        ApplicationContext context = buildContext('test.SingletonBean', SOURCE, true)
        Class<?> singletonType = context.classLoader.loadClass('test.SingletonBean')
        Class<?> prototypeType = context.classLoader.loadClass('test.PrototypeBean')
        Class<?> refreshableType = context.classLoader.loadClass('test.RefreshableBean')
        def singleton = context.getBean(singletonType)
        def target = context.getBean(refreshableType).target()

        when:
        List<BeanRegistration<?>> registrations = [context.getBeanRegistration(singletonType, null),
                                                   context.getBeanRegistration(prototypeType, null),
                                                   context.getBeanRegistration(refreshableType, null)]
        registrations.findAll { it.isOwnedByCaller() }*.close()

        then: 'the prototype was destroyed and the shared beans are the ones every holder still uses'
        prototypeType.destroyed == 1
        singletonType.destroyed == 0
        refreshableType.destroyed == 0
        context.getBean(singletonType).is(singleton)
        context.getBean(refreshableType).target().is(target)

        when: 'the singleton registration is closed regardless'
        registrations[0].close()

        then: 'the shared singleton is destroyed for every holder'
        singletonType.destroyed == 1
        !context.getBean(singletonType).is(singleton)

        cleanup:
        context.close()
    }

    void "a registration owned by the caller destroys its instance once however often it is closed"() {
        given:
        ApplicationContext context = buildContext('test.SingletonBean', SOURCE, true)
        Class<?> prototypeType = context.classLoader.loadClass('test.PrototypeBean')
        int before = prototypeType.destroyed
        BeanRegistration<?> registration = context.getBeanRegistration(prototypeType, null)

        when:
        registration.close()
        registration.close()

        then:
        registration.isOwnedByCaller()
        prototypeType.destroyed == before + 1

        cleanup:
        context.close()
    }

    private static BeanRegistration<?> registration(ApplicationContext context, String type) {
        context.getBeanRegistration(context.classLoader.loadClass(type), null)
    }
}
