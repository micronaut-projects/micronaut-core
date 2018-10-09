package io.micronaut.aop.factory

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import org.hibernate.Session
import org.hibernate.SessionFactory

class SessionProxySpec extends AbstractBeanDefinitionSpec {

    void "test create session proxy"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$AbstractBean$CurrentSessionDefinition' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.aop.interceptors.Mutating;
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
        BeanDefinition beanDefinition = buildBeanDefinition('test.$AbstractBean$SessionFactoryDefinition' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.aop.interceptors.Mutating;
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

