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
package io.micronaut.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Blocking
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor

/**
 * @author graemerocher
 * @since 1.0
 */
class InheritedAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test that annotation metadata is inherited from overridden methods for introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;

@Stub
@javax.inject.Singleton
interface MyBean extends MyInterface {

    @Override
    public String someMethod();
}

interface MyInterface {
    @Blocking
    @Executable
    public String someMethod();
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].hasAnnotation(Blocking)
        !beanDefinition.executableMethods[0].hasDeclaredAnnotation(Blocking)

    }

    void "test that annotation metadata is inherited from overridden methods for around advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;

@Mutating("someVal")
@javax.inject.Singleton
class MyBean implements MyInterface {

    private String myValue;
    
    MyBean(@Value("${foo.bar}") String val) {
        this.myValue = val;
    }
    
    @Override
    public String someMethod() {
        return myValue;
    }

}

interface MyInterface {
    @Blocking
    @Executable
    public String someMethod();
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].hasAnnotation(Blocking)

        when:
        def context = ApplicationContext.run('foo.bar':'test')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)


        then:
        instance.someMethod() == 'test'
    }
}
