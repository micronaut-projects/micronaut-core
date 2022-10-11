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
package io.micronaut.inject.scope

import io.micronaut.context.annotation.Prototype
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import jakarta.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class DefaultScopeSpec extends AbstractTypeElementSpec {

    void "test default scope no override"() {
        given:"A bean that defines no explicit scope"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.inject.scope.*;

@SomeAnn
class MyBean {
}


''')
        then:"the default scope is singleton"
        beanDefinition.hasDeclaredStereotype(Singleton)
        beanDefinition.isSingleton()
    }

    void "test default scope with override"() {
        given:"A bean that defines no explicit scope"
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.inject.scope.*;
import io.micronaut.context.annotation.*;

@SomeAnn
@Prototype
class MyBean {
}


''')
        then:"the default scope is singleton"
        !beanDefinition.hasDeclaredStereotype(AnnotationUtil.SINGLETON)
        beanDefinition.hasDeclaredStereotype(Prototype)
        !beanDefinition.isSingleton()
    }

    void "test default scope override factory"() {
        given:"A bean factory that overrides the default scope"
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBeanFactory','''
package test;

import io.micronaut.inject.scope.*;

@io.micronaut.context.annotation.Factory
@io.micronaut.context.annotation.Prototype
class MyBeanFactory {
    @SomeAnn
    @io.micronaut.context.annotation.Prototype
    MyBean myBean() {
        return new MyBean();
    } 
}

class MyBean {
}
''')

        expect:"the default scope is singleton"
        !beanDefinition.isSingleton()
        beanDefinition.scopeName.get() == Prototype.NAME
    }


    void "test default scope override factory bean"() {
        given:"A bean that defines no explicit scope"
        when:
        ClassLoader classLoader = buildClassLoader('test.MyBean', '''
package test;

import io.micronaut.inject.scope.*;

@io.micronaut.context.annotation.Factory
class MyBeanFactory {
    @SomeAnn
    @io.micronaut.context.annotation.Prototype
    MyBean myBean() {
        return new MyBean();
    } 
}

class MyBean {
}
''')
        BeanDefinition beanDefinition = classLoader
                .loadClass('test.$MyBeanFactory$MyBean0' + BeanDefinitionWriter.CLASS_SUFFIX)
                .newInstance()

        then:"the default scope is singleton"
        !beanDefinition.hasDeclaredStereotype(Singleton)
        !beanDefinition.isSingleton()
    }

    void "test default scope for @Bean annotation"() {
        given:"A bean that defines no explicit scope"
        when:
        ClassLoader classLoader = buildClassLoader('test.MyBean', '''
package test;

import io.micronaut.inject.scope.*;

@io.micronaut.context.annotation.Factory
class MyBeanFactory {
    @io.micronaut.context.annotation.Bean
    MyBean myBean() {
        return new MyBean();
    } 
}

class MyBean {
}
''')
        BeanDefinition beanDefinition = classLoader
                .loadClass('test.$MyBeanFactory$MyBean0' + BeanDefinitionWriter.CLASS_SUFFIX)
                .newInstance()

        then:"the default scope is singleton"
        !beanDefinition.hasDeclaredStereotype(Singleton)
        !beanDefinition.isSingleton()
    }

}
