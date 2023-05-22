package io.micronaut.kotlin.processing.beans

import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class AbstractBeanSpec extends Specification {

    void "test bean definitions are created for classes with only a qualifier"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test

@jakarta.inject.Named("a")
class Bean
''')
        then:
        beanDefinition != null
        !beanDefinition.isSingleton()
    }

    void "test abstract classes with only a qualifier do not generate bean definitions"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test

@jakarta.inject.Named("a")
abstract class Bean
''')
        then:
        beanDefinition == null
    }

    void "test classes with only AOP advice generate bean definitions"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test

@io.micronaut.validation.Validated
open class Bean
''')
        then:
        beanDefinition != null
    }

    void "test abstract classes with only AOP advice do not generate bean definitions"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Bean', '''
package test;

@io.micronaut.validation.Validated
abstract class Bean
''')
        then:
        beanDefinition == null
    }

}
