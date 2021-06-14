package io.micronaut.inject.errors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.aliasfor.TestAnnotation

import jakarta.inject.Named
import jakarta.inject.Qualifier

class SingletonOnEnumSpec extends AbstractTypeElementSpec {

    void "test that using @Singleton on an enum results in a compilation error"() {
        when:
        buildBeanDefinition('test.Test','''\
package test;

@jakarta.inject.Singleton
enum Test {
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Enum types cannot be defined as beans')
    }

}
