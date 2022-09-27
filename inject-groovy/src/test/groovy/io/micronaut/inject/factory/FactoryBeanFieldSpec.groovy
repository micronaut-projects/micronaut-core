package io.micronaut.inject.factory

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Unroll

class FactoryBeanFieldSpec extends AbstractBeanDefinitionSpec {
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
    $primitiveType[] totals = [ 10 ] as $primitiveType[]
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

    void "test produce bean for primitive type from field"() {
        given:
        def context = buildContext('''
package primitive.fields.factory

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton;

@Factory
class IntFactory {
    @Bean
    @Named("total")
    int total() {
        return 10;
    }
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

        def bean = getBean(context, 'primitive.fields.factory.MyBean')

        expect:
        bean.total == 10
        bean.totalFromField == 10

    }

    void "test a factory bean can be supplied from a field"() {
        given:
        ApplicationContext context = buildContext('''
package io.micronaut.inject.factory;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import io.micronaut.inject.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import javax.inject.*;

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

        getBean(context, "io.micronaut.inject.factory.Foo").name == 'one'
        getBean(context, "io.micronaut.inject.factory.Foo", Qualifiers.byName("two")).name == 'two'
        getBean(context, "io.micronaut.inject.factory.Foo", Qualifiers.byName("two")).is(
                getBean(context, "io.micronaut.inject.factory.Foo", Qualifiers.byName("two"))
        )
        getBean(context, "io.micronaut.inject.factory.Foo", Qualifiers.byName("three")).is(
                getBean(context, "io.micronaut.inject.factory.Foo", Qualifiers.byName("three"))
        )
        getBean(context, 'io.micronaut.inject.factory.TestConstructInterceptor').invoked == false
        getBean(context, "io.micronaut.inject.factory.Foo", Qualifiers.byName("four")) // around construct
        getBean(context, 'io.micronaut.inject.factory.TestConstructInterceptor').invoked == true

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
import javax.inject.*;

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
        modifier << ['private']
    }
}
