package io.micronaut.inject.factory.proxytarget

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class FactoryWithScopedProxySpec extends AbstractBeanDefinitionSpec {
    void "test that a factory that returns a class that has constructor argument and specifies AOP advise fails to compile"() {
        when:
        buildBeanDefinition('factproxy.TestFactory', '''
package factproxy;

import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean
    @io.micronaut.runtime.context.scope.ThreadLocal
    Test test() {
        return new Test("foo");
    }
}

class Test {
    Test(String name) {}
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor")
    }
}
