package io.micronaut.inject.factory.multiple

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractTypeElementSpec

class MethodSameNameSpec extends AbstractTypeElementSpec {

    void "test multiple methods called 'a' works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        ctx.getBean(A)

        then:
        noExceptionThrown()
    }

    void "test multiple definitions are created"() {
        given:
        ApplicationContext context = buildContext('test.AFactory', '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import javax.inject.Singleton;

@Factory
class AFactory {

    @Bean
    @Requires(beans=X.class, missingBeans=Y.class)
    A a(X x) {
        return new A();
    }

    @Bean
    @Requires(beans= {X.class, Y.class})
    A a(X x, Y y) {
        return new A();
    }
}

class A { }

@Singleton
class X { }

class Y { }

''')

        when:
        context.classLoader.loadClass('test.$AFactory$A0Definition')
        context.classLoader.loadClass('test.$AFactory$A1Definition')

        then:
        noExceptionThrown()
    }
}
