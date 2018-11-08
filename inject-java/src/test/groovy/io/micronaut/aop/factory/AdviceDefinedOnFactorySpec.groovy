package io.micronaut.aop.factory

import io.micronaut.context.BeanContext
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import org.hibernate.Session
import spock.lang.Specification

class AdviceDefinedOnFactorySpec extends AbstractTypeElementSpec {
    void "test advice defined at the class level of a  factory"() {
        when:"Advice is defined at the class level of the factory"
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyFactoryDefinition' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import io.micronaut.aop.simple.Mutating;
import org.hibernate.Session;

@Factory
@Mutating("name")
class MyFactory {

    
    @Bean
    String myBean(@Parameter String name) {
        return name;
    }
}

''')
        then:"The methods of the factory have AOP advice applied, but not the created beans"
        beanDefinition.executableMethods.size() == 1

    }
}
