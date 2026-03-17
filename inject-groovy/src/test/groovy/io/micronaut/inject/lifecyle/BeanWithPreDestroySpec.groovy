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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.LifeCycle
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DisposableBeanDefinition
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Created by graemerocher on 17/05/2017.
 */
class BeanWithPreDestroySpec extends AbstractBeanDefinitionSpec {

    void "test that a bean with a pre-destroy hook works"() {
        given:
        ApplicationContext context = ApplicationContext.run()

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

        cleanup:
        context.close()
    }

    void "test that a bean with a pre-destroy hook works closed on close"() {
        given:
        ApplicationContext context = ApplicationContext.run()

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

        cleanup:
        context.close()
    }

    void "test predestroy on an interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.\$FooFactory\$Foo0", """
package test

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

@Factory
class FooFactory {

    @Bean(preDestroy="close")
    Foo foo() {
        new Foo() {
            @Override
             void close()throws Exception{
                println("closed")
            }
        }
    }
}

interface Foo extends AutoCloseable {

}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
    }

    void "test predestroy on an interface method with a generic"() {
        when:
        BeanContext beanContext = buildContext( """
package test

import io.micronaut.context.LifeCycle
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

@Factory
class FooFactory {

    @Singleton
    @Bean(preDestroy="close")
    Foo foo() {
        new Foo() {

            private boolean running = true

            @Override
            boolean isRunning() {
                return running
            }

            @Override
            Foo stop() {
                running = false
                return this
            }
        }
    }
}

interface Foo extends LifeCycle<Foo> {

}
""", false)

        then:
        noExceptionThrown()
        Class<?> fooClass = beanContext.classLoader.loadClass("test.Foo")
        beanContext.getBeanDefinition(fooClass) instanceof DisposableBeanDefinition

        when:
        LifeCycle bean = beanContext.getBean(fooClass)

        then:
        bean.isRunning()

        when:
        beanContext.destroyBean(fooClass)

        then:
        !bean.isRunning()
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
