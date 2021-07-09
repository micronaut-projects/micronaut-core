package io.micronaut.inject.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.DisposableBeanDefinition

class PreDestroyCompileSpec extends AbstractTypeElementSpec {
    void "test visit @PreDestroy"() {
        when:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

class Test {

    @Inject
    Test() {  }
    
    @PreDestroy
    void close() {   }
}
''')

        then:
        definition != null
        definition.preDestroyMethods.size() == 1
        definition instanceof DisposableBeanDefinition
    }
}
