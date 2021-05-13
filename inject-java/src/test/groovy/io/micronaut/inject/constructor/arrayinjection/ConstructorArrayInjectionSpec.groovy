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
package io.micronaut.inject.constructor.arrayinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class ConstructorArrayInjectionSpec extends AbstractTypeElementSpec {

    void "test array injection with constructor"() {
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


    void "test array injection with constructor - parsing"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.B', '''
package test;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;
class B {
    private A[] all;

    @Inject
    public B(A[] all) {
        this.all = all;
    }

    public java.util.List<A> getAll() {
        return java.util.Arrays.asList(all);
    }
}

@Singleton
class A {}

''')
        then:
        beanDefinition != null
        beanDefinition.constructor.arguments.size() == 1
    }
}
