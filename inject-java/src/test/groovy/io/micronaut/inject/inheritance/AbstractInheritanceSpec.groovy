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
package io.micronaut.inject.inheritance

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import spock.lang.PendingFeature

class AbstractInheritanceSpec extends AbstractTypeElementSpec {

    void "test values are injected for abstract parent class"() {
        given:
        BeanContext context  = new DefaultBeanContext()
        context.start()

        when:"A bean is retrieved that has abstract inherited values"
        B b = context.getBean(B)

        then:"The values are injected"
        b.a != null
        b.another != null
        b.a.is(b.another)
        b.packagePrivate != null
        b.packagePrivate.is(b.another)
    }

    @PendingFeature
    void "test subclass method is injectable"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.SubClass", """
package test;

abstract class Parent {

    @jakarta.inject.Inject
    void inject(Bean bean) {
    
    }
    
    @jakarta.inject.Inject
    public void injectPublic(Bean bean) {
    
    }
    
    @jakarta.inject.Inject
    public void injectNoOverride(Bean bean) {
    
    }
}

class Middle extends Parent {

    @jakarta.inject.Inject
    public void injectNoOverride(Bean bean) {
    
    }
}

@jakarta.inject.Singleton
class SubClass extends Middle {

    @Override
    void inject(Bean bean) {
    
    }
    
    @Override
    public void injectPublic(Bean bean) {
    
    }
    
    public void injectNoOverride(Bean bean) {
    
    }
}

@jakarta.inject.Singleton
class Bean {
}
""")

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getInjectedMethods().size() == 1
        beanDefinition.getInjectedMethods()[0].name == "injectPublic"
    }
}
