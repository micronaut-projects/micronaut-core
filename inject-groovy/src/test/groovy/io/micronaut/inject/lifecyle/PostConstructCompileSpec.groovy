package io.micronaut.inject.lifecyle

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class PostConstructCompileSpec extends AbstractBeanDefinitionSpec {

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

    void "test visit constructor @Inject"() {
        when:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

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

import javax.annotation.PostConstruct;
import javax.inject.Inject;

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
