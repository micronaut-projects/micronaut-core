package io.micronaut.aop.introduction.with_around

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class Test extends AbstractTypeElementSpec {

    void "test compile"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.MyBean1","""
package test;

import io.micronaut.aop.introduction.with_around.*;

@ProxyIntroduction
@ProxyAround
public class MyBean1 {


    public MyBean1() {
    }


    public void list(String[][] multidim) {
    }
}

""")

        then:
        noExceptionThrown()
    }
}
