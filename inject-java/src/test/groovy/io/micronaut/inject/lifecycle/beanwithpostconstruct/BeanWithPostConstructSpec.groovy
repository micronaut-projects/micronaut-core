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
package io.micronaut.inject.lifecycle.beanwithpostconstruct

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class BeanWithPostConstructSpec extends AbstractTypeElementSpec {

    void "test @PreDestroy and @PostConstruct injection compile"() {
        given:"A bean that has life cycle annotations"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;
import javax.annotation.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.micronaut.context.annotation.*;

@Executable
class MyBean {
    
    Foo foo;
    
    public Foo getFoo() { return this.foo; }
    
    @PostConstruct
    public void init(Foo foo) {
        this.foo = foo;
    }
    
    @PreDestroy
    public void setFoo(Foo foo) {
        this.foo = null;
    }
    @PreDestroy
    public void close() {}
}


@jakarta.inject.Singleton
class Foo {}

''')
        then:"the state is correct"
        beanDefinition.injectedMethods.size() == 3
        beanDefinition.preDestroyMethods.size() == 2
        beanDefinition.postConstructMethods.size() == 1

    }

    void "test that a bean with a protected post construct hook that the hook is invoked"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        b.injectedFirst
        b.setupComplete
    }
}
