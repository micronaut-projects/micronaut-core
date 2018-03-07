package io.micronaut.inject.lifecyle

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.BeanInitializedEventListener
import io.micronaut.context.event.BeanInitializingEvent
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.event.BeanInitializedEventListener
import io.micronaut.context.event.BeanInitializingEvent
import spock.lang.Specification

import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class BeanInitializingEventListenerSpec extends Specification {
    void "test bean initializing event listener"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:"A bean is retrieved where a BeanInitializedEventListener is present"
        B b= context.getBean(B)

        then:"The event is triggered prior to @PostConstruct hooks"
        b.name == "CHANGED"

    }

    static class B {
        String name
    }

    @Singleton
    static class A {
        String name = "A"
    }

    @Singleton
    static class BFactory implements Provider<B> {
        String name = "original"
        boolean postConstructCalled = false
        boolean getCalled = false
        @Inject private A fieldA
        @Inject protected A anotherField
        @Inject A a
        private A methodInjected
        @Inject private injectMe(A a) {
            methodInjected = a
        }
        A getFieldA() {
            return fieldA
        }

        A getAnotherField() {
            return anotherField
        }

        A getMethodInjected() {
            return methodInjected
        }

        @PostConstruct
        void init() {
            postConstructCalled = true
            name = name.toUpperCase()
        }
        @Override
        B get() {
            getCalled = true
            return new B(name: name )
        }
    }

    @Singleton
    static class MyListener implements BeanInitializedEventListener<BFactory> {

        @Override
        BFactory onInitialized(BeanInitializingEvent<BFactory> event) {
            BFactory bean = event.bean
            assert bean.methodInjected
            assert bean.fieldA
            assert bean.anotherField
            assert bean.a
            assert !bean.postConstructCalled
            assert !bean.getCalled
            bean.name = "changed"
            return bean
        }

    }
}
