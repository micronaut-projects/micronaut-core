package io.micronaut.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class PostConstructCompileSpec extends AbstractTypeElementSpec {

    void "test that a @PostConstruct method on a type not defined as a bean doesn't create a bean"() {
        when:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.PostConstruct;

class Test {

    @PostConstruct
    void init() {
    

    }
}
''')

        then:
        definition == null
    }

    void "test visit constructor @Inject"() {
        when:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

class Test {

    @Inject
    Test() {
    

    }
}
''')

        then:
        definition != null
        definition.postConstructMethods.empty

    }

    void "test visit constructor @Inject and @PostConstruct"() {
        when:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

class Test {

    @Inject
    Test() {  }
    
    @PostConstruct
    void init() {   }
}
''')

        then:
        definition != null
        definition.postConstructMethods.size() == 1
    }
}
