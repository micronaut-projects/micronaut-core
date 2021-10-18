package io.micronaut.inject.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinitionReference
import spock.lang.Unroll

class FactoryBeanDefinitionSpec extends AbstractTypeElementSpec {

    @Unroll
    void "test produce bean for primitive #primitiveType array type from method"() {
        given:
        def context = buildContext("""
package primitive.fields.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("totals")
    $primitiveType[] totals() { 
        $primitiveType[] totals = { 10 };
        return totals;
    }
    
}

@Singleton
class MyBean {
    public final $primitiveType[] totals;

    @Inject
    @Named("totals")
    public $primitiveType[] totalsFromField;

    public $primitiveType[] totalsFromMethod;

    MyBean(@Named $primitiveType[] totals) {
        this.totals = totals;
    }
    
    @Inject
    void setTotals(@Named $primitiveType[] totals) {
        this.totalsFromMethod = totals;
    }
}
""")

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.totals[0] == 10
        bean.totalsFromField[0] == 10
        bean.totalsFromMethod[0] == 10

        where:
        primitiveType << ['int', 'short', 'long', 'double', 'float', 'byte']
    }

    void "test produce bean for primitive type from method"() {
        given:
        def context = buildContext('''
package primitive.method.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
class IntFactory {
    @Bean
    @Named("total")
    int total() { return 10; }
}

@Singleton
class MyBean {
    public final int total;
    @Inject
    @Named("total")
    public int totalFromField;
    
    MyBean(@Named int total) {
        this.total = total;
    }
}
''')

        def bean = getBean(context, 'primitive.method.factory.MyBean')

        expect:
        bean.total == 10
        bean.totalFromField == 10
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
