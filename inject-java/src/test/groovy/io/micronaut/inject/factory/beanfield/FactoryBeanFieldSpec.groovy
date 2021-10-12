package io.micronaut.inject.factory.beanfield

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Unroll

class FactoryBeanFieldSpec extends AbstractTypeElementSpec {

    void "test fail compilation for AOP advice for primitive array type from field"() {
        when:
        buildBeanDefinition('primitive.fields.factory.errors.PrimitiveFactory',"""
package primitive.fields.factory.errors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("totals")
    @Mutating("test")
    int[] totals = { 10 };
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot apply AOP advice to arrays")
    }

    void "test fail compilation for AOP advice to primitive type from field"() {
        when:
        buildBeanDefinition('primitive.fields.factory.errors.PrimitiveFactory',"""
package primitive.fields.factory.errors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

@Factory
class PrimitiveFactory {
    @Bean
    @Named("total")
    @Mutating("test")
    int totals = 10;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Cannot apply AOP advice to primitive beans")
    }

    void "test fail compilation when defining preDestroy for primitive type from field"() {
        when:
        buildBeanDefinition('primitive.fields.factory.errors.PrimitiveFactory',"""
package primitive.fields.factory.errors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import io.micronaut.aop.simple.Mutating;

@Factory
class PrimitiveFactory {
    @Bean(preDestroy="close")
    @Named("total")
    int totals = 10;
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Using 'preDestroy' is not allowed on primitive type beans")
    }

    @Unroll
    void "test produce bean for primitive #primitiveType array type from field"() {
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
    $primitiveType[] totals = { 10 };
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

    @Unroll
    void "test produce bean for primitive #primitiveType matrix array type from field"() {
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
    $primitiveType[][] totals = { new $primitiveType[] { 10 } };
}

@Singleton
class MyBean {
    public final $primitiveType[][] totals;

    @Inject
    @Named("totals")
    public $primitiveType[][] totalsFromField;

    public $primitiveType[][] totalsFromMethod;

    MyBean(@Named $primitiveType[][] totals) {
        this.totals = totals;
    }
    
    @Inject
    void setTotals(@Named $primitiveType[][] totals) {
        this.totalsFromMethod = totals;
    }
}
""")

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.totals[0][0] == 10
        bean.totalsFromField[0][0] == 10
        bean.totalsFromMethod[0][0] == 10

        where:
        primitiveType << ['int', 'short', 'long', 'double', 'float', 'byte']
    }

    @Unroll
    void "test produce bean for primitive #primitiveType type from field"() {
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
    @Named("total")
    $primitiveType total = 10;
}

@Singleton
class MyBean {
    public final $primitiveType total;

    @Inject
    @Named("total")
    public $primitiveType totalFromField;

    public $primitiveType totalFromMethod;

    MyBean(@Named $primitiveType total) {
        this.total = total;
    }
    
    @Inject
    void setTotal(@Named $primitiveType total) {
        this.totalFromMethod = total;
    }
}
""")

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.total == 10
        bean.totalFromField == 10

        where:
        primitiveType << ['int', 'short', 'long', 'double', 'float', 'byte']
    }

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
import java.util.Locale;
import jakarta.inject.Singleton;

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
    
    @Bean
    @Mutating
    Bar bar = new Bar();
}

class Bar {
    public String test(String test) {
        return test;
    }
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

@Retention(RUNTIME)
@Singleton
@Around
@interface Mutating {
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

@InterceptorBean(Mutating.class)
class TestInterceptor implements MethodInterceptor<Object, Object> {
    @Override public Object intercept(MethodInvocationContext<Object, Object> context) {
        final Object[] parameterValues = context.getParameterValues();
        parameterValues[0] = parameterValues[0].toString().toUpperCase(Locale.ENGLISH);
        System.out.println(parameterValues[0]);
        return context.proceed();
    }
}
''')

        def barBean = getBean(context, 'test.Bar')

        expect:
        barBean.test("good") == 'GOOD' // proxied
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
