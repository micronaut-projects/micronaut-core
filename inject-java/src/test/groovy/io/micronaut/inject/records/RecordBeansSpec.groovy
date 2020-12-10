package io.micronaut.inject.records

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import spock.lang.IgnoreIf

@IgnoreIf({ !jvm.isJava14Compatible() })
class RecordBeansSpec extends AbstractTypeElementSpec {

    void 'test bean that is a record'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', '''
package test;

@javax.inject.Singleton
record Test(OtherBean otherBean) {

}

@javax.inject.Singleton
class OtherBean {

}
''')

        expect:
        definition.constructor.arguments.length == 1
    }

    void 'test bean factory that returns a record'() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.TestFactory', '''
package test;

@io.micronaut.context.annotation.Factory
class TestFactory {
    @io.micronaut.context.annotation.Bean
    Test test(OtherBean otherBean) {
        return new Test(otherBean);
    }
}
record Test(OtherBean otherBean) {

}

@javax.inject.Singleton
class OtherBean {

}
''')

        expect:
        definition.constructor.arguments.length == 0
    }
}
