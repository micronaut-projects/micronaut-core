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
package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.exceptions.UnimplementedAdviceException
import io.micronaut.aop.introduction.NotImplementedAdvice
import io.micronaut.context.BeanContext
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
/**
 * @author graemerocher
 * @since 1.0
 */
class IntroductionAnnotationSpec extends AbstractTypeElementSpec {

    void 'test unimplemented introduction advice'() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;

@NotImplemented
interface MyBean {
    void test();
}



''')
        def context = BeanContext.run()
        def bean = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        when:
        bean.test()

        then:
        beanDefinition instanceof AdvisedBeanType
        beanDefinition.interceptedType.name == 'test.MyBean'
        thrown(UnimplementedAdviceException)

        cleanup:
        context.close()
    }

    void 'test unimplemented introduction advice on abstract class with concrete methods'() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.aop.simple.Mutating;

@NotImplemented
abstract class MyBean {
    abstract void test();

    public String test2() {
        return "good";
    }

    @Mutating("arg")
    public String test3(String arg) {
        return arg;
    }
}



''')
        def context = BeanContext.run()
        def bean = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)
        when:
        bean.test()


        then:
        thrown(UnimplementedAdviceException)
        NotImplementedAdvice.invoked

        when:
        NotImplementedAdvice.invoked = false

        then:
        bean.test2() == 'good'
        bean.test3() == 'changed'
        !NotImplementedAdvice.invoked

        cleanup:
        context.close()

    }

    void "test @Min annotation"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import jakarta.validation.constraints.*;

interface MyInterface{
    @Executable
    void save(@NotBlank String name, @Min(1L) int age);
    @Executable
    void saveTwo(@Min(1L) String name);
}


@Stub
@jakarta.inject.Singleton
interface MyBean extends MyInterface {
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        def saveMethod = beanDefinition.executableMethods.find { it.name == "save" }
        saveMethod.methodName == 'save'
        saveMethod.returnType.type == void.class
        saveMethod.arguments[0].getAnnotationMetadata().hasAnnotation(NotBlank)
        saveMethod.arguments[1].getAnnotationMetadata().hasAnnotation(Min)
        saveMethod.arguments[1].getAnnotationMetadata().getValue(Min, Integer).get() == 1
        def saveTwoMethod = beanDefinition.executableMethods.find { it.name == "saveTwo" }
        saveTwoMethod.methodName == 'saveTwo'
        saveTwoMethod.returnType.type == void.class
        saveTwoMethod.arguments[0].getAnnotationMetadata().hasAnnotation(Min)

    }

}
