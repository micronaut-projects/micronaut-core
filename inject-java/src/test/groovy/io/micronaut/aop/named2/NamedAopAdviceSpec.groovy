package io.micronaut.aop.named2

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.inject.qualifiers.Qualifiers

class NamedAopAdviceSpec extends AbstractTypeElementSpec {

    void "test that named beans that have AOP advice applied lookup the correct target named bean - primary included"() {
        given:
            def context = buildContext('test.java', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.aop.Logged;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

interface OtherInterface {
    String doStuff();
}

@Singleton
class OtherBean {

    @Inject @Named("first") public OtherInterface first;
    @Inject @Named("second") public OtherInterface second;
}


interface NamedInterface {
    String doStuff();
}

@ConfigurationProperties("config")
class Config {

    public Config(Inner inner) {

    }

    @ConfigurationProperties("inner")
    public static class Inner {
    }
}


@Factory
class NamedFactory {

    @EachProperty(value = "aop.test.named", primary = "default")
    @Refreshable
    NamedInterface namedInterface(@Parameter String name) {
        return () -> name;
    }


    @Named("first")
    @Logged
    @Singleton
    OtherInterface first() {
        return () -> "first";
    }

    @Named("second")
    @Logged
    @Singleton
    OtherInterface second() {
        return () -> "second";
    }

    @EachProperty("other.interfaces")
    OtherInterface third(Config config, @Parameter String name) {
        return () -> name;
    }
}



''', false, ['aop.test.named.default': 0,
             'aop.test.named.one': 1,
             'aop.test.named.two': 2,])

        def namedInterfaceClass = context.getClassLoader().loadClass('test.NamedInterface')

        expect:
        context.getBean(namedInterfaceClass) instanceof Intercepted
        context.getBean(namedInterfaceClass).doStuff() == 'default'
        context.getBean(namedInterfaceClass, Qualifiers.byName("one")).doStuff() == 'one'
        context.getBean(namedInterfaceClass, Qualifiers.byName("two")).doStuff() == 'two'
        context.getBeansOfType(namedInterfaceClass).size() == 3
        context.getBeansOfType(namedInterfaceClass).every({ it instanceof Intercepted })

        cleanup:
        context.close()
    }

}
