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
package io.micronaut.inject.method.qualifierinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition

import jakarta.inject.Qualifier

class SetterWithQualifierSpec extends AbstractTypeElementSpec {

    void "test setter with qualifier compile"() {
        given:"A bean with a setter and a qualifier"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;
import io.micronaut.inject.qualifiers.One;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Singleton
class MyBean {
    private A a;
    private A a2;

    @Inject
    public void setA(@One A a) {
        this.a = a;
    }

    @Inject
    public void setAnother(@Named("twoA") A a2) {
        this.a2 = a2;
    }

    public A getA() {
        return a;
    }

    public A getA2() {
        return a2;
    }
}


interface A {

}



@Singleton
class OneA implements A {

}


@Singleton
class TwoA implements A {

}


''')
        then:"the state is correct"
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].arguments[0].getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).isPresent()
        beanDefinition.injectedMethods[1].arguments[0].getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).isPresent()
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
}
