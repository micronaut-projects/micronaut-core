package io.micronaut.inject.field

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

class FieldInjectionSpec extends Specification {

    void "test injection via private field with interface"() {
        given:
        BeanContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        C c =  context.getBean(C)

        then:"The implementation is injected"
        c.a != null

        cleanup:
        context.close()
    }

    void "test injection via private field of property with replacement"() {
        given:
        BeanContext context = ApplicationContext.run(['from.config': 'greeting', 'greeting': 'Hello'])

        when:"A bean is obtained that has a setter with @Inject"
        C c =  context.getBean(C)

        then:"The implementation is injected"
        c.value == "Hello"

        cleanup:
        context.close()
    }

    static interface A {

    }

    @Singleton
    static class AImpl implements A {

    }

    static class C {
        @Inject
        private A a

        @Nullable
        @Property(name = '${from.config}')
        private String value

        A getA() {
            return a
        }

        String getValue() {
            return value
        }
    }

}

