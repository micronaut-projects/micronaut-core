package io.micronaut.inject.factory.beanfield

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Unroll

class FactoryBeanFieldSpec extends AbstractTypeElementSpec {
    void "test a factory bean can be supplied from a field"() {
        given:
        ApplicationContext context = buildContext('test.TestFactory$TestField', '''\
package test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.factory.enummethod.TestEnum;
import jakarta.inject.*;

@Factory
class TestFactory$TestField {

    @Singleton
    @Bean
    @io.micronaut.context.annotation.Primary
    Foo one = new Foo("one");
    
    // final fields are implicitly singleton
    @Bean
    @Named("two")
    final Foo two = new Foo("two");
    
    // non-final fields are prototype
    @Bean
    @Named("three")
    Foo three = new Foo("three");
    
    @SomeMeta
    @Bean
    Foo four = new Foo("four");
}

class Foo {
    final String name;
    Foo(String name) {
        this.name = name;
    }
}

@Retention(RUNTIME)
@Singleton
@Named("four")
@AroundConstruct
@interface SomeMeta {
}

@Singleton
@InterceptorBean(SomeMeta.class)
class TestConstructInterceptor implements ConstructorInterceptor<Object> {
    boolean invoked = false;
    Object[] parameters;
    
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        parameters = context.getParameterValues();
        return context.proceed();
    }
} 
''')

        expect:

        getBean(context, "test.Foo").name == 'one'
        getBean(context, "test.Foo", Qualifiers.byName("two")).name == 'two'
        getBean(context, "test.Foo", Qualifiers.byName("two")).is(
                getBean(context, "test.Foo", Qualifiers.byName("two"))
        )
        getBean(context, "test.Foo", Qualifiers.byName("three")).is(
                getBean(context, "test.Foo", Qualifiers.byName("three"))
        )
        getBean(context, 'test.TestConstructInterceptor').invoked == false
        getBean(context, "test.Foo", Qualifiers.byName("four")) // around construct
        getBean(context, 'test.TestConstructInterceptor').invoked == true

        cleanup:
        context.close()
    }

    void 'test fail compilation on bean field primitive'() {
        when:
        buildBeanDefinition('testprim.TestFactory', '''
package testprim;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Factory
class TestFactory {
    @Bean
    int junk;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Produced type from a bean factory cannot be primitive')
    }

    @Unroll
    void 'test fail compilation on invalid modifier #modifier'() {
        when:
        buildBeanDefinition('invalidmod.TestFactory', """
package invalidmod;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Factory
class TestFactory {
    @Bean
    $modifier Test test;
}

class Test {}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("cannot be ")
        e.message.contains(modifier)

        where:
        modifier << ['private', 'protected', 'static']
    }
}
