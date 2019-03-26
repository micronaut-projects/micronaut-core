package io.micronaut.inject.factory

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext

class FactoryEnumSpec extends AbstractBeanDefinitionSpec {

    void "test a factory can return an enum"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestEnum', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.factory.TestEnum;

@Factory
class TestFactory$TestEnum {

    @javax.inject.Singleton
    TestEnum testEnum() {
        return TestEnum.ONE;
    }
}

''')

        expect:
        context.containsBean(TestEnum)
        context.getBean(TestEnum) == TestEnum.ONE

        cleanup:
        context.close()
    }
}
