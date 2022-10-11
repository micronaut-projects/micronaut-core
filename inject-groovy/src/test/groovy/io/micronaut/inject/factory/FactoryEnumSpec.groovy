package io.micronaut.inject.factory

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext

class FactoryEnumSpec extends AbstractBeanDefinitionSpec {

    void "test a factory can return an enum"() {
        given:
        ApplicationContext context = buildContext('''
package testenumf1;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.factory.TestEnum;

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
