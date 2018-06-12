/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.runtime.context.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.inject.BeanDefinition
import io.micronaut.support.AbstractBeanDefinitionSpec

import javax.inject.Scope
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ThreadLocalScopeSpec extends AbstractBeanDefinitionSpec {

    void "test parse bean definition data"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ThreadLocalBean', '''
package test;

import io.micronaut.runtime.context.scope.*;

@ThreadLocal
class ThreadLocalBean {

}
''')

        then:
        beanDefinition.getAnnotationNameByStereotype(Scope).get() == ThreadLocal.name

    }

    void "test bean definition data"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("test")
        BeanDefinition aDefinition = applicationContext.getBeanDefinition(A)

        expect:
        aDefinition.getAnnotationNameByStereotype(Scope).isPresent()
        aDefinition.getAnnotationNameByStereotype(Scope).get() == ThreadLocal.name

    }

    void "test thread local scope on class"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("test")
        B b = applicationContext.getBean(B)

        when:
        b.a.num = 2

        boolean isolated = false
        Thread.start {
            isolated = b.a.total() == 0
            b.a.setNum(4)
            assert b.a.total() == 4
        }.join()


        then:
        b.a.total() == 2
        isolated

    }

    void "test thread local scope on interface"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("test")
        BAndInterface b = applicationContext.getBean(BAndInterface)

        when:
        b.a.num = 2

        boolean isolated = false
        Thread.start {
            isolated = b.a.total() == 0
            b.a.setNum(4)
            assert b.a.total() == 4
        }.join()


        then:
        b.a.total() == 2
        isolated

    }


    void "test thread local scope on factory"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("test")
        BAndFactory b = applicationContext.getBean(BAndFactory)

        when:
        b.a.num = 2

        boolean isolated = false
        Thread.start {
            isolated = b.a.total() == 0
            b.a.setNum(4)
            assert b.a.total() == 4
        }.join()


        then:
        b.a.total() == 2
        isolated

    }
}

@ThreadLocal
class A {
    int num

    int total() {
        return num
    }
}

@Singleton
class B {
    private A a

    B(A a) {
        this.a = a
    }

    A getA() {
        return a
    }
}

interface IA {
    void setNum(int num)

    int total()
}

@ThreadLocal
class AImpl implements IA {
    int num

    int total() {
        return num
    }
}

@Singleton
class BAndInterface {
    private IA a

    BAndInterface(IA a) {
        this.a = a
    }

    IA getA() {
        return a
    }
}

interface IA2 {
    void setNum(int num)

    int total()
}

class A2Impl implements IA2 {
    int num

    int total() {
        return num
    }
}

@Factory
class  IA2Factory {
    @Bean
    @ThreadLocal
    IA2 a() {
        return new A2Impl()
    }
}

@Singleton
class BAndFactory {
    private IA2 a

    BAndFactory(IA2 a) {
        this.a = a
    }

    IA2 getA() {
        return a
    }
}