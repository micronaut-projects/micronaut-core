package io.micronaut.jdt

import io.micronaut.annotation.processing.test.jdt.AbstractJdtTypeElementSpec
import io.micronaut.inject.BeanDefinition

class JdtSmokeSpec extends AbstractJdtTypeElementSpec {

    void "test a simple bean definition can be built with the JDT compiler"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Simple', '''
package test;

import jakarta.inject.Singleton;

@Singleton
class Simple {
    private final String name;

    Simple(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
''')
        expect:
        definition != null
        definition.constructor.arguments.length == 1
        definition.constructor.arguments[0].name == 'name'
    }
}
