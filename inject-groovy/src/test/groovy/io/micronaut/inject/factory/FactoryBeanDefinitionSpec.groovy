package io.micronaut.inject.factory

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.BeanDefinitionReference

class FactoryBeanDefinitionSpec extends AbstractBeanDefinitionSpec {

    void "test a factory bean with static method or field"() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;

@Factory
class TestFactory {

    @Bean
    @Prototype
    static Bar1 bar() {
        return new Bar1();
    }

    @Bean
    @Prototype
    static Bar2 bar = new Bar2();
}

class Bar1 {
}

class Bar2 {
}


''')

        when:
        def bar1BeanDefinition = context.getBeanDefinitions(context.classLoader.loadClass('test.Bar1'))
                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

                .find {it.getDeclaringType().get().simpleName.contains("TestFactory")}

        def bar1 = getBean(context, 'test.Bar1')
        def bar2 = getBean(context, 'test.Bar2')

        then:
        bar1 != null
        bar2 != null
        bar1BeanDefinition.getScope().get() == Prototype.class

        cleanup:
        context.close()
    }

    void "test is context"() {
        given:
        BeanDefinitionReference definition = buildBeanDefinitionReference('io.micronaut.inject.factory.Test$MyFunc0','''\
package io.micronaut.inject.factory;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    @Context
    java.util.function.Function<String, Integer> myFunc() {
        return (str) -> 10;
    }
}

''')
        expect:
        definition != null
        definition.isContextScope()
    }
}
