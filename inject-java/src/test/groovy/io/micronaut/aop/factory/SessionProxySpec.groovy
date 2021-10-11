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
package io.micronaut.aop.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import org.hibernate.Session
import org.hibernate.SessionFactory

class SessionProxySpec extends AbstractTypeElementSpec {

    void "test create session proxy"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$AbstractBean$CurrentSession0' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.aop.simple.Mutating;
import org.hibernate.Session;

@Factory
class AbstractBean {

    @Mutating("name")
    @Bean
    Session currentSession() {
        return null;
    }
}

''')
        // make sure all the public method are implemented
        def clazz = beanDefinition.getBeanType()
        int count = 1 // proxy methods
        def interfaces = ReflectionUtils.getAllInterfaces(Session.class)
        interfaces += Session.class
        for(i in interfaces) {
            for(m in i.declaredMethods) {
                count++
                assert clazz.getDeclaredMethod(m.name, m.parameterTypes)
            }
        }

//        for(m in clazz.declaredMethods) {
//            println m
//        }
        then:
        beanDefinition.isProxy()

    }

    void "test create session factory proxy"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$AbstractBean$SessionFactory0' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.aop.simple.Mutating;
import org.hibernate.*;

@Factory
class AbstractBean {

    @Mutating("name")
    @Bean
    SessionFactory sessionFactory() {
        return null;
    }
}

''')
        // make sure all the public method are implemented
        def clazz = beanDefinition.getBeanType()
        int count = 1 // proxy methods
        def interfaces = ReflectionUtils.getAllInterfaces(SessionFactory.class)
        interfaces += SessionFactory.class
        for(i in interfaces) {
            for(m in i.declaredMethods) {
                count++
                assert clazz.getDeclaredMethod(m.name, m.parameterTypes)
            }
        }

//        for(m in clazz.declaredMethods) {
//            println m
//        }
        then:
//        count == clazz.declaredMethods.size()  // plus the proxy methods
        beanDefinition.isProxy()

    }
}
