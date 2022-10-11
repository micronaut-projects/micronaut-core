package io.micronaut.inject.factory.proxytarget

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import spock.lang.Unroll

class FactoryWithScopedProxySpec extends AbstractBeanDefinitionSpec {
    void "test mock bean compiles"() {
        expect:"mock bean to compile"
        buildBeanDefinition('mockbeantest.TestFactory',"""
package mockbeantest;

import io.micronaut.context.annotation.*;
import io.micronaut.aop.Around;
import static io.micronaut.aop.Around.ProxyTargetConstructorMode.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;
import io.micronaut.test.annotation.MockBean;

@Factory
class TestFactory {

    @MockBean(Test.class)
    Test test() {
        return new Test("foo", 10);
    }
}

class Test {
    public String name;
    Test(String name, int prim) {
        this.name = name;
    }
    
    public String name() {
        return this.name;
    }
}
""")

    }

    @Unroll
    void "test a factory that defines AOP advice and constructor arguments compiled with a #type if set to do so"() {
        when:
        def context = buildContext("""
package factproxy2;

import io.micronaut.context.annotation.*;
import io.micronaut.aop.Around;
import static io.micronaut.aop.Around.ProxyTargetConstructorMode.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;

@Factory
class TestFactory {

    @Bean
    @TestAnn
    Test test() {
        return new Test("foo", 10);
    }
}

class Test {
    public String name;
    Test(String name, int prim) {
        this.name = name;
    }
    
    public String name() {
        return this.name;
    }
}

@Around(proxyTargetMode = $type, proxyTarget=true)
@Retention(RUNTIME)
@interface TestAnn {}
""")
        def bean = getBean(context, 'factproxy2.Test')

        then:"Name is null because fields can't be proxied"
        bean.name == null

        and:"Method proxied onto actual instance"
        bean.name() == 'foo'

        cleanup:
        context.close()

        where:
        type << ["WARN", "ALLOW"]
    }


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
