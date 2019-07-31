package io.micronaut.inject.lifecycle

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class PostConstructCompileSpec extends AbstractTypeElementSpec {

    void "test that a @PostConstruct method on a type not defined as a bean doesn't create a bean"() {
        when:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''
package test;

import javax.annotation.PostConstruct;

class Test {

    @PostConstruct
    void init() {
    

    }
}
''')

        then:
        definition == null
    }
}
