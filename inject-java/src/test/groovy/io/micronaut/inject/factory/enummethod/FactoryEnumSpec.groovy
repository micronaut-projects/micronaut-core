package io.micronaut.inject.factory.enummethod

import io.micronaut.context.ApplicationContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class FactoryEnumSpec extends AbstractTypeElementSpec {

    void "test a factory can return an enum"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestEnum', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.factory.enummethod.TestEnum;

@Factory
class TestFactory$TestEnum {

    @jakarta.inject.Singleton
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
