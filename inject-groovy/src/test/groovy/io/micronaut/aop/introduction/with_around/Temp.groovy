package io.micronaut.aop.introduction.with_around

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class Temp extends AbstractBeanDefinitionSpec {

    void "test"() {
        BeanDefinition beanDefinition = buildBeanDefinition("test.MyBean1" + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test

import io.micronaut.aop.introduction.with_around.*

@ProxyIntroduction
@ProxyAround
class MyBean1 {

    Long id
    String name
}

""")
        expect:
        beanDefinition != null
    }
}
