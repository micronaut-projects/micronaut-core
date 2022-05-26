package io.micronaut.inject.factory.multiple

import io.micronaut.context.ApplicationContext
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.writer.BeanDefinitionWriter

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
import jakarta.inject.Singleton;

@Factory
class AFactory {

    @Singleton
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
        context.classLoader.loadClass('test.$AFactory$A0' + BeanDefinitionWriter.CLASS_SUFFIX)
        context.classLoader.loadClass('test.$AFactory$A1' + BeanDefinitionWriter.CLASS_SUFFIX)

        then:
        noExceptionThrown()
    }
}
