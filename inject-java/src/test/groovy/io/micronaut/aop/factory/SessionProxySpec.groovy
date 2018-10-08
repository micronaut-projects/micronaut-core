package io.micronaut.aop.factory


import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

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
        then:
        beanDefinition.isProxy()
    }
}
