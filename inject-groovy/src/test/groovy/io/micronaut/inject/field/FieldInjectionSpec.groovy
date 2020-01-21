package io.micronaut.inject.field

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.context.event.BeanContextEvent
import io.micronaut.context.exceptions.BeanContextException
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

class FieldInjectionSpec extends Specification {

    void "test bean injection via private field"() {
        given:
        BeanContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        C c =  context.getBean(C)

        then:"The implementation is injected"
        c.a != null

        cleanup:
        context.close()
    }

    void "test configuration value injection via private field"() {
        given:
        BeanContext context = ApplicationContext.run(['greeting': 'Hello'])

        when:"A bean is obtained that has a setter with @Inject"
        C2 c =  context.getBean(C2)

        then:"The implementation is injected"
        c.value == "Hello"

        cleanup:
        context.close()
    }

    void "test configuration property injection via private field"() {
        given:
        BeanContext context = ApplicationContext.run(['greeting': 'Hello'])

        when:"A bean is obtained that has a setter with @Inject"
        C3 c =  context.getBean(C3)

        then:"The implementation is injected"
        c.property == "Hello"

        cleanup:
        context.close()
    }

    void "test injection with no bean/property found and not nullable"() {
        BeanContext context = ApplicationContext.run()

        when:
        E e = context.getBean(E)

        then:
        e.d == null
        e.value == "Default greeting"
        e.property == "Default greeting"

        when:
        context.getBean(C2)

        then:
        thrown(BeanContextException)

        when:
        context.getBean(C3)

        then:
        thrown(BeanContextException)

        when:
        context.getBean(C4)

        then:
        thrown(BeanContextException)

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

        A getA() {
            return a
        }
    }

    static class C2 {
        @Value('${greeting}')
        private String value

        String getValue() {
            return value
        }
    }

    static class C3 {
        @Property(name = 'greeting')
        private String property

        String getProperty() {
            return property
        }
    }

    static class C4 {
        @Inject
        private D d

        D getD() {
            return d
        }
    }

    static interface D {

    }

    static class E {
        @Inject
        @Nullable
        private D d

        @Nullable
        @Value('${greeting}')
        private String value = "Default greeting"

        @Nullable
        @Property(name = 'greeting')
        private String property = "Default greeting"

        D getD() {
            return d
        }

        String getValue() {
            return value
        }

        String getProperty() {
            return property
        }
    }

}

