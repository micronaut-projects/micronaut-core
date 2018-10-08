package io.micronaut.aop.factory

import io.micronaut.aop.Intercepted
import io.micronaut.context.DefaultBeanContext
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor
import org.hibernate.Session
import org.hibernate.SessionFactory

class SessionProxySpec extends AbstractTypeElementSpec {

    void "test create session proxy"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$AbstractBean$CurrentSessionDefinition' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
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

        then:
        beanDefinition.isProxy()

    }
}
