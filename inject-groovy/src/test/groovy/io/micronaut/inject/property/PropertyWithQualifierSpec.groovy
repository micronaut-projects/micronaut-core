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
package io.micronaut.inject.property

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.One

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Created by graemerocher on 15/05/2017.
 */
class PropertyWithQualifierSpec extends AbstractBeanDefinitionSpec {

    void "test compile property with qualifier"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.property.MyBean', '''
package io.micronaut.inject.property;

import io.micronaut.inject.qualifiers.*

@javax.inject.Singleton
class MyBean  {
    @javax.inject.Inject
    @One
    AnotherBean injected
}

@javax.inject.Singleton
@One
class AnotherBean implements SomeInterface{
    
}
interface SomeInterface {

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].name == "injected"
        beanDefinition.injectedMethods[0].arguments[0].synthesize(One)

    }

    void "test that a property with a qualifier is injected correctly"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a instanceof OneA
        b.a2 instanceof TwoA
    }

    static class B {
        @Inject @One A a
        @Inject @Named('twoA') A a2
    }

    static  interface A {

    }

}


@Singleton
class OneA implements PropertyWithQualifierSpec.A {

}
@Singleton
class TwoA implements PropertyWithQualifierSpec.A {

}



