package io.micronaut.aop.compile

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.ProxyBeanDefinition

class FactoryWithScopedProxySpec extends AbstractTypeElementSpec {

    void "test that a factory that returns a scoped proxy is a proxy bean definition"() {
        when:
        def definition = buildBeanDefinition('test.TestFactory$Test0', '''
package test;
import io.micronaut.context.annotation.*;
import io.micronaut.runtime.context.scope.Refreshable;

@Factory
class TestFactory {

    @Refreshable
    @Bean
    Test test() {
        return new TestImpl();
    }   
}

interface Test {}

class TestImpl implements Test {}

''')

        then:
        !(definition instanceof ProxyBeanDefinition)
    }
}
