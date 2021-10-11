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
package io.micronaut.inject.method.arrayinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class SetterArrayInjectionSpec extends AbstractTypeElementSpec {

    void "test setter injection compile"() {
        given:"A bean that has setter injection"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Executable
class MyBean {
    
    @jakarta.inject.Inject
    public void setFoo(Foo foo) {}
}


@jakarta.inject.Singleton
class Foo {}

''')
        then:"the default scope is singleton"
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].name == 'setFoo'
    }

    void "test injection via setter that takes an array"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b =  context.getBean(B)

        then:
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
        b.all.contains(context.getBean(AnotherImpl))
    }
}
